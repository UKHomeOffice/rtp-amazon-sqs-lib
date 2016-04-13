package uk.gov.homeoffice.amazon.sqs.subscription

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}
import akka.testkit.TestActorRef
import org.json4s.jackson.JsonMethods._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemContext
import uk.gov.homeoffice.amazon.sqs.{EmbeddedSQSServer, Message, Queue}
import uk.gov.homeoffice.json.JsonFormats

class SubscriberActorSpec(implicit ev: ExecutionEnv) extends Specification with JsonFormats {
  trait Context extends ActorSystemContext with EmbeddedSQSServer {
    val queue = create(new Queue("test-queue"))

    def promised[R](result: Promise[R], processed: R) = {
      result success processed
      processed
    }
  }

  "Subscriber actor" should {
    "receive a string and process it" in new Context {
      val input = "blah"
      val result = Promise[Try[String]]()

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) {
          def process(m: Message) = promised(result, Success(m.content))
        }
      }

      actor.underlyingActor receive createMessage(input)

      result.future must beEqualTo(Success(input)).await
    }

    "reject a string" in new Context {
      val input = "blah"
      val result = Promise[Try[String]]()

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) {
          def process(m: Message) = promised(result, Failure(new Exception(m.content)))
        }
      }

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