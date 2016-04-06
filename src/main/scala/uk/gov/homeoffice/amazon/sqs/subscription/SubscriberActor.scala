package uk.gov.homeoffice.amazon.sqs.subscription

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Try}
import akka.actor.Actor
import com.amazonaws.services.sqs.model.Message
import grizzled.slf4j.Logging
import uk.gov.homeoffice.amazon.sqs._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._
import uk.gov.homeoffice.json.Json

abstract class SubscriberActor(subscriber: Subscriber) extends Actor with QueueCreation with Json with Logging {
  implicit val sqsClient = subscriber.sqsClient
  val queue = subscriber.queue
  val publisher = new Publisher(queue)(sqsClient)

  /**
    * Override this for custom publication of an error upon invalid processing of a message from the message queue.
    * By default, error publication will publish a given exception as JSON along with the original message
    */
  val publishError: PartialFunction[(Throwable, Message), Any] = { case (throwable, message) =>
    publisher publishError compact(render(
      ("error-message" -> asJson(throwable)) ~
      ("original-message" -> message.toString)
    ))
  }

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
        case Nil => context.system.scheduler.scheduleOnce(10 seconds, self, Subscribe)
        case messages => messages foreach { self ! _ }
      }

    case message: Message =>
      process(message) map { _ => delete(message) } recoverWith {
        case t: Throwable =>
          publishError(t, message)
          Failure(t)
      }

      self ! Subscribe

    case other =>
      error(s"""Received message that is "${`not-amazon-sqs-message`}" (must have been sent to this actor directly instead of coming from an Amazon SQS queue): $other""")
  }

  /**
    * Override this method for custom deletion of messages from the message queue, or even to not delete a message.
    */
  def delete(m: Message) = sqsClient.deleteMessage(queueUrl(queue.queueName), m.getReceiptHandle)

  /**
    * By default, error publication will publish a given exception as JSON along with the original message.
    * @param t Throwable that indicates what went wrong with processing a received message
    * @param message Message the message that could not be processed
    */
  final def publishError(t: Throwable, message: Message): Any = {
    (publishError orElse publishError)(t -> message)
    delete(message)
  }
}