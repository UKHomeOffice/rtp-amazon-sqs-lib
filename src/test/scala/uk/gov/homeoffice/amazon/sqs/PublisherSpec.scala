package uk.gov.homeoffice.amazon.sqs

import com.amazonaws.services.sqs.model.Message
import org.specs2.mutable.Specification

class PublisherSpec extends Specification {
  "Publisher" should {
    "publish some text" in new SQSTestServer {
      val publisher = new Publisher with SQSTestClient with SQSTestQueue
      publisher publish "Testing 1, 2, 3"

      val subscriber = new Subscriber with SQSTestClient with SQSTestQueue

      subscriber.receive must beLike {
        case Seq(m: Message) => m.getBody mustEqual "Testing 1, 2, 3"
      }
    }
  }
}