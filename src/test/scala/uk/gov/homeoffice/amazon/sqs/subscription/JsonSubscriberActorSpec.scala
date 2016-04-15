package uk.gov.homeoffice.amazon.sqs.subscription

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.{Success, Try}
import akka.actor.Props
import akka.testkit.TestActorRef
import play.api.http.Status.OK
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemContext
import uk.gov.homeoffice.amazon.sqs._
import uk.gov.homeoffice.concurrent.PromiseOps
import uk.gov.homeoffice.json._

class JsonSubscriberActorSpec(implicit ev: ExecutionEnv) extends Specification with JsonFormats with PromiseOps {
  trait Context extends ActorSystemContext with EmbeddedSQSServer {
    val queue = create(new Queue("test-queue"))

    trait MyJsonSubscription extends JsonSubscription {
      this: SubscriberActor =>

      val jsonSchema = JsonSchema(
        ("id" -> "http://www.bad.com/schema") ~
          ("$schema" -> "http://json-schema.org/draft-04/schema") ~
          ("type" -> "object") ~
          ("properties" ->
            ("input" ->
              ("type" -> "string")))
      )
    }
  }

  "JSON subscriber (test) actor" should {
    "receive any old JSON and process it" in new Context {
      val result = Promise[Try[String]]()

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) with JsonSubscription {
          val jsonSchema = EmptyJsonSchema

          def process(json: JValue) = result <~ Success("Well done!")
        }
      }

      actor.underlyingActor receive createMessage(compact(render(JObject("input" -> JString("blah")))))

      result.future must beEqualTo(Success("Well done!")).await
    }

    "receive valid JSON and process it" in new Context {
      val result = Promise[Try[String]]()

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) with MyJsonSubscription {
          def process(json: JValue) = result <~ Success("Well done!")
        }
      }

      actor.underlyingActor receive createMessage(compact(render(JObject("input" -> JString("blah")))))

      result.future must beEqualTo(Success("Well done!")).await
    }

    "receive JSON and fail to validate" in new Context {
      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) with MyJsonSubscription {
          def process(json: JValue) = throw new Exception("Should not process as JSON should have failed schema validation")
        }
      }

      val input = JObject("input" -> JInt(0))
      actor.underlyingActor receive createMessage(compact(render(input)))

      val errorSubscriber = new Subscriber(queue)

      errorSubscriber.receiveErrors must beLike {
        case Seq(m: Message) =>
          val `error-message` = parse(m.content) \ "error-message"

          `error-message` \ "json" mustEqual input
          (`error-message` \ "error").extract[String] must contain("error: instance type (integer) does not match any allowed primitive type")
      }
    }
  }

  "JSON subscriber actor" should {
    "receive valid JSON and process it" in new Context {
      val result = Promise[Try[String]]()

      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) with MyJsonSubscription {
          def process(json: JValue) = result <~ Success("Well done!")
        }
      }

      val publisher = new Publisher(queue)
      publisher publish compact(render(JObject("input" -> JString("blah"))))

      result.future must beEqualTo(Success("Well done!")).await
    }

    "receive JSON and fail to validate" in new Context {
      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) with MyJsonSubscription {
          def process(json: JValue) = throw new Exception("Should not process as JSON should have failed schema validation")
        }
      }

      val publisher = new Publisher(queue)

      val input = JObject("input" -> JInt(0))
      publisher publish compact(render(input))

      val errorSubscriber = new Subscriber(queue)

      eventually {
        errorSubscriber.receiveErrors must beLike {
          case Seq(m: Message) =>
            val `error-message` = parse(m.content) \ "error-message"

            `error-message` \ "json" mustEqual input
            (`error-message` \ "error").extract[String] must contain("error: instance type (integer) does not match any allowed primitive type")
        }
      }
    }

    "receive valid JSON from a RESTful POST and process it" in new Context with REST {
      val result = Promise[Try[String]]()

      val response = wsClient.url(s"$sqsHost/queue/${queue.queueName}")
        .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
        .post(params("Action" -> "SendMessage", "MessageBody" -> compact(render(JObject("input" -> JString("blah")))))) map { response =>
        response.status
      }

      response must beEqualTo(OK).await

      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) with MyJsonSubscription {
          def process(json: JValue) = result <~ Success("Well done!")
        }
      }

      result.future must beEqualTo(Success("Well done!")).awaitFor(3 seconds)
    }

    "receive invalid JSON from a RESTful POST" in new Context with REST {
      val input = JObject("input" -> JInt(0))

      val response = wsClient.url(s"$sqsHost/queue/${queue.queueName}")
        .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
        .post(params("Action" -> "SendMessage", "MessageBody" -> compact(render(input)))) map { response =>
        response.status
      }

      response must beEqualTo(OK).await

      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) with MyJsonSubscription {
          def process(json: JValue) = throw new Exception("Should not process as JSON should have failed schema validation")
        }
      }

      val errorSubscriber = new Subscriber(queue)

      eventually {
        errorSubscriber.receiveErrors must beLike {
          case Seq(m: Message) =>
            val `error-message` = parse(m.content) \ "error-message"

            `error-message` \ "json" mustEqual input
            (`error-message` \ "error").extract[String] must contain("error: instance type (integer) does not match any allowed primitive type")
        }
      }
    }
  }
}