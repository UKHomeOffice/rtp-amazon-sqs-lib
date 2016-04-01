package uk.gov.homeoffice.amazon.sqs

import scala.xml.Elem
import play.api.http.Status.OK
import com.amazonaws.services.sqs.model.Message
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemContext

class PublisherRESTSpec(implicit ev: ExecutionEnv) extends Specification {
  "Restful client" should {
    "post some text" in new ActorSystemContext with EmbeddedSQSServer with REST {
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
      }.await
    }

    "post some JSON" in new ActorSystemContext with EmbeddedSQSServer with REST {
      val json =
        ("key1" -> "value1") ~
        ("key2" -> "value2")

      val queue = create(new Queue("test-queue"))

      val result = wsClient.url(s"$sqsHost/queue/${queue.queueName}")
                           .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
                           .post(params("Action" -> "SendMessage", "MessageBody" -> compact(render(json)))) map { response =>
        (response.status, response.xml)
      }

      result must beLike[(Int, Elem)] {
        case (status, xml) =>
          status mustEqual OK
          xml.head.label mustEqual "SendMessageResponse"

          val subscriber = new Subscriber(queue)

          subscriber.receive must beLike {
            case Seq(m: Message) => parse(m.getBody) mustEqual json
          }
      }.await
    }
  }
}