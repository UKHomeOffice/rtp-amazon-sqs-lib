package uk.gov.homeoffice.amazon.sqs.subscription

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef}
import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import uk.gov.homeoffice.amazon.sqs._
import uk.gov.homeoffice.json.Json

/**
  * Subscribe to SQS messages.
  * Process a message by implementing "receive".
  * Your receive function should at least handle Message. However, it should also handle your own custom protocol of messages.
  * After a Message has been processed, you should probably delete the message from the queue and if necessary handle any errors such as publishing an error message.
  * @param subscriber Subscriber Amazon SQS subscriber which wraps connection functionality to an instance of SQS.
  * @param filters Zero to many functions of Message => Option[Message] where a filter can act on and/or alter a given message and either pass it through for further processing or not.
  * @param listeners  Seq[ActorRef] Registered listeners will be informed of all messages received by this actor.
  * <img src="/doc-files/classhierarchy.png">
  */
abstract class SubscriberActor(subscriber: Subscriber, filters: (Message => Option[Message])*)(implicit listeners: Seq[ActorRef] = Seq.empty[ActorRef]) extends Actor with QueueCreation with Json with ActorLogging {
  implicit val sqsClient = subscriber.sqsClient
  val queue = subscriber.queue
  val publisher = new Publisher(queue)

  /** Upon instantiating this actor, create its associated queues and start subscribing. */
  override def preStart() = {
    super.preStart()
    info("Initialising...")
    create(queue)
    self ! Subscribe
  }

  /**
    * All messages received by your actor will be intercepted by this function.
    * @param receive Receive functionality being intercepted.
    * @param message Any The message that has been received for processing.
    */
  override final def aroundReceive(receive: Receive, message: Any): Unit = {
    /** Intercept message received so that it can be broadcast to any listeners. */
    val broadcast: PartialFunction[Any, Any] = {
      case m @ Subscribe =>
        m // This type of message is not broadcast.

      case m =>
        listeners foreach { _ ! m }
        m
    }

    /** This actor only knows about Subscribe or Message - note that Message will be passed onto a concrete actor's custom "receive" function. */
    val handle: PartialFunction[Any, Any] = {
      case Subscribe =>
        subscriber receive match {
          case Nil => context.system.scheduler.scheduleOnce(10 seconds, self, Subscribe) // TODO 10 seconds to be configurable
          case messages => messages foreach { self ! _ }
        }

      case sqsMessage: SQSMessage =>
        self ! new Message(sqsMessage)

      case message: Message =>
        log.info(s"Received subscribed message: $message")

        filters.foldLeft(Option(message)) { (m, f) =>
          m flatMap f
        } match {
          case Some(msg) => receive.applyOrElse(msg, unhandled)
          case _ => warn(s"Filtered out message $message")
        }

        self ! Subscribe

      case msg =>
        log.info(s"Received message: $msg")
        receive.applyOrElse(msg, unhandled)
    }

    (broadcast andThen handle)(message)
  }

  /**
    * The actor's "main" method to process messages in its own mailbox (i.e. its own message queue).
    * You need to handle a subscribed Message as well as your custom protocol.
    */
  def receive: Receive

  /**
    * Override this method for custom deletion of messages from the message queue, or even to not delete a message.
    * NOTE That by default this method is not called unless you either:
    * (i)   Use Akka messaging e.g. have your implenting actor of this class fire a Processed to itself, or have another actor fire said message to this actor.
    * (ii)  Mixin an AfterProcess such as DefaultAfterProcess
    * (iii) Call this method directly from your own process method.
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
    * @param t       Throwable that indicates what went wrong with processing a received message
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