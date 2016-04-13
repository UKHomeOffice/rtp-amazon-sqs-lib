package uk.gov.homeoffice.amazon.sqs.subscription

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Try}
import akka.actor.Actor
import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import grizzled.slf4j.Logging
import uk.gov.homeoffice.amazon.sqs._
import uk.gov.homeoffice.json.Json

/**
  * Subscribe to SQS messages
  * @param subscriber Amazon SQS subscriber which wraps connection functionality to an instance of SQS
  */
abstract class SubscriberActor(subscriber: Subscriber) extends Actor with QueueCreation with Json with Logging {
  implicit val sqsClient = subscriber.sqsClient
  val queue = subscriber.queue
  val publisher = new Publisher(queue)(sqsClient)

  /**
    * Default functionality of publishing an error.
    * An error is published as JSON with the format:
    * <pre>
    *   {
    *     "error-message":    { ... },
    *     "original-message": { ... }
    *   }
    * </pre>
    */
  private val defaultPublishError: PartialFunction[(Throwable, Message), Any] = { case (throwable, message) =>
    debug(s"Publishing error ${throwable.getMessage}")

    publisher publishError compact(render(
      ("error-message" -> toJson(throwable)) ~
      ("original-message" -> message.toString)
    ))
  }

  /**
    * Override this for custom publication of an error upon invalid processing of a message from the message queue.
    * By default, error publication will publish a given exception as JSON along with the original message.
    * Note that being a partial function, if your override is not actually run, then defaultPublishError will be.
    * Your override could of course match on everything (case _ =>) whereby you can decide not to publish errors.
    */
  val publishError: PartialFunction[(Throwable, Message), Any] = defaultPublishError

  /**
    * Implement your functionality i.e. process a received Message
    * @param m Message
    * @return Try of the outcome of processing the given Message
    */
  def process(m: Message): Try[_]

  /**
    * Upon instantiating this actor, create its associated queues and start subscribing
    */
  override def preStart() = {
    super.preStart()
    create(queue)
    self ! Subscribe
  }

  /**
    * The actor's "main" method to process messages in its own mailbox (i.e. its own message queue)
    */
  final def receive: Receive = {
    case Subscribe =>
      subscriber.receive match {
        case Nil => context.system.scheduler.scheduleOnce(10 seconds, self, Subscribe) // TODO 10 seconds to be configurable
        case messages => messages foreach { self ! _ }
      }

    case sqsMessage: SQSMessage =>
      self ! new Message(sqsMessage)

    case message: Message =>
      debug(s"Received message: $message")

      process(message) map { _ =>
        debug(s"Processed message: $message")
        delete(message)
      } recoverWith {
        case t: Throwable =>
          debug(s"Failed to process message: $message")
          publishError(t, message)
          Failure(t)
      }

      self ! Subscribe

    case other =>
      error(s"""Received message that is "${`not-amazon-sqs-message`}" (must have been sent to this actor directly instead of coming from an Amazon SQS queue): $other""")
  }

  /**
    * Override this method for custom deletion of messages from the message queue, or even to not delete a message.
    * @param message Message to delete
    */
  def delete(message: Message) = sqsClient.deleteMessage(queueUrl(queue.queueName), message.sqsMessage.getReceiptHandle)

  /**
    * By default, error publication will publish a given exception as JSON along with the original message.
    * @param t Throwable that indicates what went wrong with processing a received message
    * @param message Message the message that could not be processed
    */
  final def publishError(t: Throwable, message: Message): Any = {
    (publishError orElse defaultPublishError)(t -> message)
    delete(message)
  }
}