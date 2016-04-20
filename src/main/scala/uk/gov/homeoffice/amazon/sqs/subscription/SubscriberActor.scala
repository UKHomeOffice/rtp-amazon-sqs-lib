package uk.gov.homeoffice.amazon.sqs.subscription

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import uk.gov.homeoffice.amazon.sqs._
import uk.gov.homeoffice.amazon.sqs.subscription.Protocol.{Processed, ProcessingError}
import uk.gov.homeoffice.json.Json

/**
  * Subscribe to SQS messages.
  * Process a message by implementing method "process".
  * Then after processing a message, you may (should) perform "after processing" functionality such as deleting the processed message from the SQS queue and if necessary publishing any error to an associated error queue.
  *
  * @param subscriber Subscriber Amazon SQS subscriber which wraps connection functionality to an instance of SQS.
  * @param listeners Seq[ActorRef] Registered listeners will be informed of all messages received by this actor.
  */
abstract class SubscriberActor(subscriber: Subscriber)(implicit listeners: Seq[ActorRef] = Seq.empty[ActorRef]) extends Actor with QueueCreation with Json with ActorLogging with AfterProcess {
  implicit val sqsClient = subscriber.sqsClient
  val queue = subscriber.queue
  val publisher = new Publisher(queue)

  /**
    * Implement your functionality i.e. process a received Message
    *
    * @param m Message
    * @return Future The outcome of processing the given Message
    */
  def process(m: Message): Future[_]

  /**
    * Upon instantiating this actor, create its associated queues and start subscribing
    */
  override def preStart() = {
    super.preStart()
    create(queue)
    self ! Subscribe
  }

  /**
    * Intercept message received so that they can be broadcast to any listeners.
    * @return PartialFunction[Any, Any] Where the input is a message and the output is the same message.
    */
  def intercept: PartialFunction[Any, Any] = {
    case message =>
      listeners foreach { _ ! message}
      message
  }

  /**
    * The actor's "main" method to process messages in its own mailbox (i.e. its own message queue)
    */
  def receive: Receive = intercept andThen LoggingReceive {
    case Subscribe =>
      subscriber.receive match {
        case Nil => context.system.scheduler.scheduleOnce(10 seconds, self, Subscribe) // TODO 10 seconds to be configurable
        case messages => messages foreach { self ! _ }
      }

    case sqsMessage: SQSMessage =>
      self ! new Message(sqsMessage)

    case message: Message =>
      log.info(s"Received message: $message")
      process(message) andThen afterProcess(message)

      self ! Subscribe

    case Processed(message) =>
      log.info(s"Processed message: $message")
      delete(message)

    case ProcessingError(throwable, message) =>
      log.info(s"Failed to process message: $message")
      publishError(throwable, message)

    case other =>
      log.error(s"""Received message that is "${`not-amazon-sqs-message`}" (must have been sent to this actor directly instead of coming from an Amazon SQS queue): $other""")
  }

  /**
    * After processing does nothing unless you say so by mixing in an AfterProcess, or simply overriding this method.
    *
    * @param message Message being processed.
    * @tparam R The type of result from processing
    * @return PartialFunction[Try[R], _]
    */
  override def afterProcess[R](message: Message): PartialFunction[Try[R], _] = {
    case _ =>
  }

  /**
    * Override this method for custom deletion of messages from the message queue, or even to not delete a message.
    * NOTE That by default this method is not called unless you either:
    * (i)   Use Akka messaging e.g. have your implenting actor of this class fire a Processed to itself, or have another actor fire said message to this actor.
    * (ii)  Mixin an AfterProcess such as DefaultAfterProcess
    * (iii) Call this method directly from your own process method.
    *
    * @param message Message to delete
    * @return Message that was successfully deleted
    */
  def delete(message: Message): Message = {
    sqsClient.deleteMessage(queueUrl(queue.queueName), message.sqsMessage.getReceiptHandle)
    message
  }

  /**
    * By default, error publication will publish a given exception as JSON along with the original message.
    * Override this for custom publication of an error upon invalid processing of a message from the message queue.
    * NOTE That by default this method is not called unless you either:
    * (i)   Use Akka messaging e.g. have your implenting actor of this class fire a ProcessingError to itself, or have another actor fire said message to this actor.
    * (ii)  Mixin an AfterProcess such as DefaultAfterProcess
    * (iii) Call this method directly from your own process method.
    * EXTRA NOTE If you do override this method, you will probably want to call delete(message) - if you don't, the original message will hang around.
    *
    * @param t Throwable that indicates what went wrong with processing a received message
    * @param message Message the message that could not be processed
    * @return Message that could not be processed and has been successfully published as an error
    *
    * An error is published (by default) as JSON with the format:
    * <pre>
    *   {
    *     "error-message":    { ... },
    *     "original-message": { ... }
    *   }
    * </pre>
    */
  def publishError(t: Throwable, message: Message): Message = {
    log.info(s"Publishing error: ${t.getMessage}")

    publisher publishError compact(render(
      ("error-message" -> toJson(t)) ~
      ("original-message" -> message.toString)
    ))

    delete(message)
  }
}