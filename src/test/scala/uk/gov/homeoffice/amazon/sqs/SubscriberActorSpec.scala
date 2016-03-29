package uk.gov.homeoffice.amazon.sqs

import scala.concurrent.Promise
import akka.testkit.TestActorRef
import com.amazonaws.services.sqs.model.Message
import org.json4s.JValue
import org.json4s.JsonAST.{JObject, JString}
import org.json4s.jackson.JsonMethods._
import org.scalactic.{Bad, ErrorMessage, Good, Or}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemContext

class SubscriberActorSpec(implicit ev: ExecutionEnv) extends Specification {
  def promised[R](result: Promise[R], processed: R) = {
    result.success(processed)
    processed
  }

  "Subscriber actor" should {
    "receive a string" in new ActorSystemContext with SQSTestServer {
      val input = "blah"
      val result = Promise[String Or ErrorMessage]()

      val process = (m: Message) => promised(result, Good(m.getBody))

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(createQueue(new Queue("test-queue"))))(process)
      }

      actor.underlyingActor receive new Message().withBody(input)

      result.future must beEqualTo(Good(input)).await
    }

    "reject a string" in new ActorSystemContext with SQSTestServer {
      val input = "blah"
      val result = Promise[String Or ErrorMessage]()

      val process = (m: Message) => promised(result, Bad(m.getBody))

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(createQueue(new Queue("test-queue"))))(process)
      }

      actor.underlyingActor receive new Message().withBody(input)

      result.future must beEqualTo(Bad(input)).await
    }

    "receive JSON" in new ActorSystemContext with SQSTestServer {
      val input = JObject("input" -> JString("blah"))
      val result = Promise[JValue Or ErrorMessage]()

      val process = (m: Message) => promised(result, Good(parse(m.getBody)))

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(createQueue(new Queue("test-queue"))))(process)
      }

      actor.underlyingActor receive new Message().withBody(compact(render(input)))

      result.future must beEqualTo(Good(input)).await
    }
  }
}