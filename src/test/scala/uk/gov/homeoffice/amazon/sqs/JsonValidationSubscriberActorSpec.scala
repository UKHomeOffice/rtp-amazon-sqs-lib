package uk.gov.homeoffice.amazon.sqs

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}
import akka.testkit.TestActorRef
import com.amazonaws.services.sqs.model.Message
import org.json4s._
import org.json4s.JsonAST.JObject
import org.json4s.jackson.JsonMethods.{parse => toJson, _}
import org.scalactic.{Bad, ErrorMessage, Good, Or}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemContext
import uk.gov.homeoffice.json.{JsonError, JsonSchema, OrOps}
import org.json4s.JsonDSL._

class JsonValidationSubscriberActorSpec(implicit ev: ExecutionEnv) extends Specification {
  val schema = JsonSchema(
    ("id" -> "http://www.bad.com/schema") ~
    ("$schema" -> "http://json-schema.org/draft-04/schema") ~
    ("type" -> "object") ~
    ("properties" ->
      ("input" ->
        ("type" -> "string")))
  )

  def parse(s: String): Try[JValue] = Try {
    toJson(s)
  } recoverWith {
    case t: Throwable => Failure(new Exception(pretty(render(JsonError(error = Some("Invalid JSON format"), throwable = Some(t)).asJson))))
  }

  def promised[R](result: Promise[R], processed: R) = {
    result.success(processed)
    processed
  }

  "Subscriber actor" should {
    "receive JSON and validate" in new ActorSystemContext with SQSTestServer {
      //val input = JObject("input" -> JString("blah"))
      val input = JObject("input" -> JInt(0), "extra" -> JString("blah"))
      val result = Promise[JValue Or ErrorMessage]()

      // process: Message => Result Or ErrorMessage

      val processJson: JValue => Try[String] =
        json => Success("well done")

      def process[R](p: JValue => Try[R])(m: Message): R Or ErrorMessage = {
        (for {
          json <- parse(m.getBody)
          validatedJson <- schema.validate(json).badMap { jsonError =>
            println(s"===> Failed JSON is $json")
            new Exception(pretty(render(jsonError.asJson)))
          }.toTry
          processedJson <- p(json)
        } yield processedJson) match {
          case Success(r) =>
            println(s"===> Success $r")
            Good(r)

          case Failure(t) =>
            println(s"===> Failure $t")
            Bad(t.getMessage)
        }
      }


      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(createQueue(new Queue("test-queue"))))(process(processJson))
      }

      actor.underlyingActor receive createMessage(compact(render(input)))

      //result.future must beEqualTo(Good(input)).await
      ok
    }
  }
}