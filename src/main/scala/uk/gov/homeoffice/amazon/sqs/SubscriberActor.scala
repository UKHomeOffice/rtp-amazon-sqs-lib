package uk.gov.homeoffice.amazon.sqs

import akka.actor.Actor
import com.amazonaws.services.sqs.model.Message
import org.scalactic.{ErrorMessage, Or}

object SubscriberActor {
  case object Subscribe
}

class SubscriberActor[Result](subscriber: Subscriber)(process: Message => Result Or ErrorMessage) extends Actor {
  import SubscriberActor._

  val publisher = new Publisher(subscriber.queue)(subscriber.sqsClient)

  override def preStart() = {
    super.preStart()
    createQueue(subscriber.queue.queueName)
    createQueue(subscriber.queue.errorQueueName)
    self ! Subscribe
  }

  final def receive: Receive = {
    case Subscribe =>
      subscriber.receive foreach { self ! _ }

    case m: Message =>
      process(m) badMap publisher.publishError
      self ! Subscribe
  }

  private def createQueue(queueName: String) = subscriber.sqsClient.createQueue(queueName)
}