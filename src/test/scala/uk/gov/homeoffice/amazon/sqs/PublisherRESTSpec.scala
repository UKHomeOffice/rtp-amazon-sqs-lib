package uk.gov.homeoffice.amazon.sqs

import scala.concurrent.duration._
import scala.xml.Elem
import play.api.http.Status.OK
import com.amazonaws.services.sqs.model.Message
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemContext

class PublisherRESTSpec(implicit ev: ExecutionEnv) extends Specification {
  "Restful client" should {
    "post some text" in new ActorSystemContext with SQSEmbeddedServer with REST {
      val queue = create(new Queue("test-queue"))

      val result = wsClient.url(s"$sqsHost/queue/${queue.queueName}")
                           .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
                           .post(params("Action" -> "SendMessage", "MessageBody" -> "Testing 1, 2, 3")) map { response =>
        (response.status, response.xml)
      }

      result must beLike[(Int, Elem)] {
        case (status, xml) =>
          status mustEqual OK
          xml.head.label mustEqual "SendMessageResponse"

          val subscriber = new Subscriber(queue)

          subscriber.receive must beLike {
            case Seq(m: Message) => m.getBody mustEqual "Testing 1, 2, 3"
          }
      }.awaitFor(3 seconds)
    }
  }
}