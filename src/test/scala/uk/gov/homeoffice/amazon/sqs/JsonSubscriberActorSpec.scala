package uk.gov.homeoffice.amazon.sqs

import scala.concurrent.Promise
import akka.testkit.TestActorRef
import com.amazonaws.services.sqs.model.Message
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalactic.{ErrorMessage, Good, Or}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemContext
import uk.gov.homeoffice.amazon.sqs.message.JsonProcessor
import uk.gov.homeoffice.json.JsonSchema

class JsonSubscriberActorSpec(implicit ev: ExecutionEnv) extends Specification {
  trait JsonToStringProcessor extends JsonProcessor[String] {
    val jsonSchema = JsonSchema(
      ("id" -> "http://www.bad.com/schema") ~
      ("$schema" -> "http://json-schema.org/draft-04/schema") ~
      ("type" -> "object") ~
      ("properties" ->
        ("input" ->
          ("type" -> "string")))
    )
  }

  def promised[R](result: Promise[R], processed: R) = {
    result.success(processed)
    processed
  }

  "Subscriber actor" should {
    "receive JSON and fail to validate" in new ActorSystemContext with SQSTestServer {
      val input = JObject("input" -> JInt(0), "extra" -> JString("blah"))
      val result = Promise[String Or ErrorMessage]()

      val queue = createQueue(new Queue("test-queue"))

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) with JsonToStringProcessor {
          def process(json: JValue) = promised(result, Good("Well Done!")).badMap(_ => new Exception).toTry
        }
      }

      val errorSubscriber = new Subscriber(queue)

      actor.underlyingActor receive createMessage(compact(render(input)))

      errorSubscriber.receiveErrors must beLike {
        case Seq(m: Message) => m.getBody must contain("error: instance type (integer) does not match any allowed primitive type")
      }
    }

    "receive valid JSON" in new ActorSystemContext with SQSTestServer {
      val input = JObject("input" -> JString("blah"))
      val result = Promise[String Or ErrorMessage]()

      val queue = createQueue(new Queue("test-queue"))

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) with JsonToStringProcessor {
          def process(json: JValue) = promised(result, Good("Well Done!")).badMap(_ => new Exception).toTry
        }
      }

      actor.underlyingActor receive createMessage(compact(render(input)))

      result.future must beEqualTo(Good("Well Done!")).await
    }
  }
}