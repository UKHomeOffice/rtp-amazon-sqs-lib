package uk.gov.homeoffice.amazon.sqs.subscription

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.{Success, Try}
import akka.actor.Props
import akka.testkit.TestActorRef
import play.api.http.Status.OK
import com.amazonaws.services.sqs.model.Message
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemContext
import uk.gov.homeoffice.amazon.sqs.{EmbeddedSQSServer, Publisher, Queue, REST}
import uk.gov.homeoffice.json.{JsonFormats, JsonSchema}
import uk.gov.homeoffice.process.Processor

class JsonSubscriberActorSpec(implicit ev: ExecutionEnv) extends Specification with JsonFormats {
  val jsonSchema = JsonSchema(
    ("id" -> "http://www.bad.com/schema") ~
      ("$schema" -> "http://json-schema.org/draft-04/schema") ~
      ("type" -> "object") ~
      ("properties" ->
        ("input" ->
          ("type" -> "string")))
  )

  def promised[R](result: Promise[R], processed: R) = {
    result success processed
    processed
  }

  "JSON subscriber (test) actor" should {
    "receive valid JSON and process it" in new ActorSystemContext with EmbeddedSQSServer {
      val input = JObject("input" -> JString("blah"))
      val result = Promise[Try[String]]()

      trait JsonToStringProcessor extends Processor[JValue, String] {
        override def process(in: JValue): Try[String] = promised(result, Success("Well done!"))
      }

      val actor = TestActorRef {
        new JsonSubscriberActor(new Subscriber(create(new Queue("test-queue"))), jsonSchema) with JsonToStringProcessor
      }

      actor.underlyingActor receive createMessage(compact(render(input)))

      result.future must beEqualTo(Success("Well done!")).await
    }

    "receive JSON and fail to validate" in new ActorSystemContext with EmbeddedSQSServer {
      val input = JObject("input" -> JInt(0))
      val result = Promise[Try[String]]()

      trait JsonToStringProcessor extends Processor[JValue, String] {
        override def process(in: JValue): Try[String] = throw new Exception("Should not happen")
      }

      val queue = create(new Queue("test-queue"))

      val actor = TestActorRef {
        new JsonSubscriberActor(new Subscriber(queue), jsonSchema) with JsonToStringProcessor
      }

      actor.underlyingActor receive createMessage(compact(render(input)))

      val errorSubscriber = new Subscriber(queue)

      errorSubscriber.receiveErrors must beLike {
        case Seq(m: Message) =>
          val `error-message` = parse(m.getBody) \ "error-message"

          `error-message` \ "json" mustEqual input
          (`error-message` \ "error").extract[String] must contain("error: instance type (integer) does not match any allowed primitive type")
      }
    }
  }

  "JSON subscriber actor" should {
    "receive valid JSON and process it" in new ActorSystemContext with EmbeddedSQSServer {
      val input = JObject("input" -> JString("blah"))
      val result = Promise[Try[String]]()

      trait JsonToStringProcessor extends Processor[JValue, String] {
        override def process(in: JValue): Try[String] = promised(result, Success("Well done!"))
      }

      val queue = create(new Queue("test-queue"))

      system actorOf Props {
        new JsonSubscriberActor(new Subscriber(queue), jsonSchema) with JsonToStringProcessor
      }

      val publisher = new Publisher(queue)
      publisher publish compact(render(input))

      result.future must beEqualTo(Success("Well done!")).await
    }

    "receive JSON and fail to validate" in new ActorSystemContext with EmbeddedSQSServer {
      val input = JObject("input" -> JInt(0))
      val result = Promise[Try[String]]()

      trait JsonToStringProcessor extends Processor[JValue, String] {
        override def process(in: JValue): Try[String] = throw new Exception("Should not happen")
      }

      val queue = create(new Queue("test-queue"))

      system actorOf Props {
        new JsonSubscriberActor(new Subscriber(queue), jsonSchema) with JsonToStringProcessor
      }

      val publisher = new Publisher(queue)
      publisher publish compact(render(input))

      val errorSubscriber = new Subscriber(queue)

      eventually {
        errorSubscriber.receiveErrors must beLike {
          case Seq(m: Message) =>
            val `error-message` = parse(m.getBody) \ "error-message"

            `error-message` \ "json" mustEqual input
            (`error-message` \ "error").extract[String] must contain("error: instance type (integer) does not match any allowed primitive type")
        }
      }
    }

    "receive valid JSON from a RESTful POST and process it" in new ActorSystemContext with EmbeddedSQSServer with REST {
      val input = JObject("input" -> JString("blah"))
      val result = Promise[Try[String]]()

      trait JsonToStringProcessor extends Processor[JValue, String] {
        override def process(in: JValue): Try[String] = {
          promised(result, Success("Well done!"))
        }
      }

      val queue = create(new Queue("test-queue"))

      val response = wsClient.url(s"$sqsHost/queue/${queue.queueName}")
                             .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
                             .post(params("Action" -> "SendMessage", "MessageBody" -> compact(render(input)))) map { response =>
        response.status
      }

      response must beEqualTo(OK).await

      system actorOf Props {
        new JsonSubscriberActor(new Subscriber(queue), jsonSchema) with JsonToStringProcessor
      }

      result.future must beEqualTo(Success("Well done!")).awaitFor(3 seconds)
    }

    "receive invalid JSON from a RESTful POST" in new ActorSystemContext with EmbeddedSQSServer with REST {
      val input = JObject("input" -> JInt(0))
      val result = Promise[Try[String]]()

      trait JsonToStringProcessor extends Processor[JValue, String] {
        override def process(in: JValue): Try[String] = throw new Exception("Should not happen")
      }

      val queue = create(new Queue("test-queue"))

      val response = wsClient.url(s"$sqsHost/queue/${queue.queueName}")
                             .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
                             .post(params("Action" -> "SendMessage", "MessageBody" -> compact(render(input)))) map { response =>
        response.status
      }

      response must beEqualTo(OK).await

      system actorOf Props {
        new JsonSubscriberActor(new Subscriber(queue), jsonSchema) with JsonToStringProcessor
      }

      val errorSubscriber = new Subscriber(queue)

      eventually {
        errorSubscriber.receiveErrors must beLike {
          case Seq(m: Message) =>
            val `error-message` = parse(m.getBody) \ "error-message"

            `error-message` \ "json" mustEqual input
            (`error-message` \ "error").extract[String] must contain("error: instance type (integer) does not match any allowed primitive type")
        }
      }
    }
  }
}