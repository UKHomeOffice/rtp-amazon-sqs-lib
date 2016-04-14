package uk.gov.homeoffice.amazon.sqs.subscription

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}
import akka.testkit.TestActorRef
import org.json4s.jackson.JsonMethods._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemContext
import uk.gov.homeoffice.amazon.sqs.{EmbeddedSQSServer, Message, Queue, ResultPromise}
import uk.gov.homeoffice.json.JsonFormats

class SubscriberActorSpec(implicit ev: ExecutionEnv) extends Specification with JsonFormats with ResultPromise {
  trait Context extends ActorSystemContext with EmbeddedSQSServer {
    val queue = create(new Queue("test-queue"))
  }

  "Subscriber actor" should {
    "receive a string and process it" in new Context {
      val result = Promise[Try[String]]()

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) {
          def process(m: Message) = result success m.content
        }
      }

      val input = "blah"
      actor.underlyingActor receive createMessage(input)

      result.future must beEqualTo(Success(input)).await
    }

    "reject a string" in new Context {
      val result = Promise[Try[String]]()

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) {
          def process(m: Message) = result failed new Exception(m.content)
        }
      }

      val input = "blah"
      actor.underlyingActor receive createMessage(input)

      result.future must beLike[Try[String]] {
        case Failure(t) => t.getMessage mustEqual input
      }.await

      val errorSubscriber = new Subscriber(queue)

      errorSubscriber.receiveErrors must beLike {
        case Seq(m: Message) =>
          (parse(m.content) \ "error-message" \ "errorStackTrace" \ "errorMessage").extract[String] mustEqual input
      }
    }
  }
}