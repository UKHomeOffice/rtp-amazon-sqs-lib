package uk.gov.homeoffice.amazon.sqs

import com.amazonaws.services.sqs.model.Message
import org.specs2.mutable.Specification

class PublisherSpec extends Specification {
  "Publisher" should {
    "publish some text" in new SQSTestServer {
      trait TestQueue extends Queue {
        val queueName = "test-queue"
      }

      val publisher = new Publisher with SQSTestClient with TestQueue
      publisher.publish("Testing 1, 2, 3")

      val subscriber = new Subscriber with SQSTestClient with TestQueue

      subscriber.receive must beLike {
        case Seq(m: Message) =>
          println(m)
          m.getBody mustEqual "Testing 1, 2, 3"
      }
    }
  }
}