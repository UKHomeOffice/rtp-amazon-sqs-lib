package uk.gov.homeoffice.amazon.sqs

import scala.concurrent.Promise
import akka.actor.Props
import akka.testkit.TestActorRef
import play.api.http.Status.OK
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

  "Subscriber (test) actor" should {
    "receive JSON and fail to validate" in new ActorSystemContext with EmbeddedSQSServer {
      val input = JObject("input" -> JInt(0))
      val result = Promise[String Or ErrorMessage]()

      val queue = create(new Queue("test-queue"))

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) with JsonToStringProcessor {
          def process(json: JValue) = promised(result, Good("Well Done!")).badMap(_ => new Exception).toTry
        }
      }

      actor.underlyingActor receive createMessage(compact(render(input)))

      val errorSubscriber = new Subscriber(queue)

      errorSubscriber.receiveErrors must beLike {
        case Seq(m: Message) => m.getBody must contain("error: instance type (integer) does not match any allowed primitive type")
      }
    }

    "receive valid JSON" in new ActorSystemContext with EmbeddedSQSServer {
      val input = JObject("input" -> JString("blah"))
      val result = Promise[String Or ErrorMessage]()

      val queue = create(new Queue("test-queue"))

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) with JsonToStringProcessor {
          def process(json: JValue) = promised(result, Good("Well Done!")).badMap(_ => new Exception).toTry
        }
      }

      actor.underlyingActor receive createMessage(compact(render(input)))

      result.future must beEqualTo(Good("Well Done!")).await
    }
  }

  "Subscriber actor" should {
    "receive JSON and fail to validate" in new ActorSystemContext with EmbeddedSQSServer {
      val input = JObject("input" -> JInt(0))
      val result = Promise[String Or ErrorMessage]()

      val queue = create(new Queue("test-queue"))

      val publisher = new Publisher(queue)

      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) with JsonToStringProcessor {
          def process(json: JValue) = promised(result, Good("Well Done!")).badMap(_ => new Exception).toTry
        }
      }

      publisher publish compact(render(input))

      val errorSubscriber = new Subscriber(queue)

      eventually {
        errorSubscriber.receiveErrors must beLike {
          case Seq(m: Message) => m.getBody must contain("error: instance type (integer) does not match any allowed primitive type")
        }
      }
    }

    "receive valid JSON" in new ActorSystemContext with EmbeddedSQSServer {
      val input = JObject("input" -> JString("blah"))
      val result = Promise[String Or ErrorMessage]()

      val queue = create(new Queue("test-queue"))

      val publisher = new Publisher(queue)

      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) with JsonToStringProcessor {
          def process(json: JValue) = promised(result, Good("Well Done!")).badMap(_ => new Exception).toTry
        }
      }

      publisher publish compact(render(input))

      result.future must beEqualTo(Good("Well Done!")).await
    }

    "receive valid JSON from a RESTful POST" in new ActorSystemContext with EmbeddedSQSServer with REST {
      val input = JObject("input" -> JString("blah"))
      val result = Promise[String Or ErrorMessage]()

      val queue = create(new Queue("test-queue"))

      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) with JsonToStringProcessor {
          def process(json: JValue) = promised(result, Good("Well Done!")).badMap(_ => new Exception).toTry
        }
      }

      val response = wsClient.url(s"$sqsHost/queue/${queue.queueName}")
                           .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
                           .post(params("Action" -> "SendMessage", "MessageBody" -> compact(render(input)))) map { response =>
        response.status
      }

      response must beEqualTo(OK).await
      result.future must beEqualTo(Good("Well Done!")).await
    }

    "receive invalid JSON from a RESTful POST" in new ActorSystemContext with EmbeddedSQSServer with REST {
      val input = JObject("input" -> JInt(0))
      val result = Promise[String Or ErrorMessage]()

      val queue = create(new Queue("test-queue"))

      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) with JsonToStringProcessor {
          def process(json: JValue) = promised(result, Good("Well Done!")).badMap(_ => new Exception).toTry
        }
      }

      val response = wsClient.url(s"$sqsHost/queue/${queue.queueName}")
                             .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
                             .post(params("Action" -> "SendMessage", "MessageBody" -> compact(render(input)))) map { response =>
        response.status
      }

      response must beEqualTo(OK).await

      val errorSubscriber = new Subscriber(queue)

      eventually {
        errorSubscriber.receiveErrors must beLike {
          case Seq(m: Message) => m.getBody must contain("error: instance type (integer) does not match any allowed primitive type")
        }
      }
    }
  }
}