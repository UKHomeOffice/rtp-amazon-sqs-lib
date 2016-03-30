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
import uk.gov.homeoffice.amazon.sqs.message.MessageProcessor

class SubscriberActorSpec(implicit ev: ExecutionEnv) extends Specification {
  def promised[R](result: Promise[R], processed: R) = {
    result.success(processed)
    processed
  }

  "Subscriber actor" should {
    "receive a string" in new ActorSystemContext with SQSTestServer {
      val input = "blah"
      val result = Promise[String Or ErrorMessage]()

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(createQueue(new Queue("test-queue")))) with MessageProcessor[String] {
          def process(message: Message) = promised(result, Good(message.getBody))
        }
      }

      actor.underlyingActor receive createMessage(input)

      result.future must beEqualTo(Good(input)).await
    }

    "reject a string" in new ActorSystemContext with SQSTestServer {
      val input = "blah"
      val result = Promise[String Or ErrorMessage]()

      val queue = createQueue(new Queue("test-queue"))

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) with MessageProcessor[String] {
          def process(message: Message): String Or ErrorMessage = promised(result, Bad(message.getBody))
        }
      }

      val errorSubscriber = new Subscriber(queue)

      actor.underlyingActor receive createMessage(input)

      result.future must beEqualTo(Bad(input)).await

      errorSubscriber.receiveErrors must beLike {
        case Seq(m: Message) => m.getBody mustEqual input
      }
    }

    "receive JSON" in new ActorSystemContext with SQSTestServer {
      val input = JObject("input" -> JString("blah"))
      val result = Promise[JValue Or ErrorMessage]()

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(createQueue(new Queue("test-queue")))) with MessageProcessor[JValue] {
          def process(message: Message): JValue Or ErrorMessage = promised(result, Good(parse(message.getBody)))
        }
      }

      actor.underlyingActor receive createMessage(compact(render(input)))

      result.future must beEqualTo(Good(input)).await
    }
  }
}