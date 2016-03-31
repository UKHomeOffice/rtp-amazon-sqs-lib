package uk.gov.homeoffice.amazon.sqs

import com.amazonaws.services.sqs.model.Message
import org.specs2.mutable.Specification

class PublisherSpec extends Specification {
  "Publisher" should {
    "publish some text" in new SQSEmbeddedServer {
      val queue = create(new Queue("test-queue"))

      val publisher = new Publisher(queue)
      publisher publish "Testing 1, 2, 3"

      val subscriber = new Subscriber(queue)

      subscriber.receive must beLike {
        case Seq(m: Message) => m.getBody mustEqual "Testing 1, 2, 3"
      }
    }
  }
}