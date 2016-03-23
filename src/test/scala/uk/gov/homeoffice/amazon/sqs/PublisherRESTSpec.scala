package uk.gov.homeoffice.amazon.sqs

import java.util.concurrent.TimeUnit
import com.amazonaws.services.sqs.model.Message
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemContext

class PublisherRESTSpec(implicit ev: ExecutionEnv) extends Specification {
  "Publisher" should {
    "post some text" in new ActorSystemContext with SQSTestServer with REST {
      val subscriber = new Subscriber with SQSTestClient with SQSTestQueue

      wsClient.url(s"$sqsHost/queue/${subscriber.queueName}")
        .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
        .post(params("Action" -> "SendMessage", "MessageBody" -> "Testing 1, 2, 3")) map { response =>
        println(s"==> THE RESPONSE = $response")
        //(response.status, response.json)
      }

      TimeUnit.SECONDS.sleep(2)

      subscriber.receive must beLike {
        case Seq(m: Message) =>
          println(m)
          m.getBody mustEqual "Testing 1, 2, 3"
      }
    }
  }
}