package uk.gov.homeoffice.amazon.sqs

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.Actor
import com.amazonaws.services.sqs.model.Message
import org.scalactic.ErrorMessage
import uk.gov.homeoffice.amazon.sqs.message.MessageProcessor

object SubscriberActor {
  case object Subscribe
}

class SubscriberActor(subscriber: Subscriber) extends Actor {
  this: MessageProcessor[_] =>

  import SubscriberActor._

  implicit val sqsClient = subscriber.sqsClient
  val queue = subscriber.queue
  val publisher = new Publisher(queue)(sqsClient)

  val deleteMessage = (m: Message) => sqsClient.deleteMessage(queueUrl(queue.queueName), m.getReceiptHandle)

  val publishErrorMessage = (e: ErrorMessage, m: Message) => {
    publisher.publishError(e)
    deleteMessage(m)
  }

  override def preStart() = {
    super.preStart()
    createQueue(queue.queueName)
    createQueue(queue.errorQueueName)
    self ! Subscribe
  }

  final def receive: Receive = {
    case Subscribe =>
      subscriber.receive match {
        case Nil => context.system.scheduler.scheduleOnce(10 seconds, self, Subscribe)
        case messages => messages foreach { self ! _ }
      }

    case m: Message =>
      process(m) map { _ => deleteMessage } badMap { e => publishErrorMessage(e, m) }
      self ! Subscribe
  }

  private def createQueue(queueName: String) = sqsClient createQueue queueName
}