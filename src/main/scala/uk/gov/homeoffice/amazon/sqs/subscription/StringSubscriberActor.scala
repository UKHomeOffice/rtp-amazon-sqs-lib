package uk.gov.homeoffice.amazon.sqs.subscription
import scala.util.Try
import com.amazonaws.services.sqs.model.Message
import uk.gov.homeoffice.process.Processor

class StringSubscriberActor(subscriber: Subscriber) extends SubscriberActor(subscriber) {
  this: Processor[String, _] =>

  override def process(m: Message): Try[_] = process(m.getBody)
}