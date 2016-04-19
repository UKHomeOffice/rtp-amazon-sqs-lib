package uk.gov.homeoffice.amazon.sqs.subscription

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.Try
import akka.actor.Props
import akka.testkit.TestActorRef
import org.json4s.JValue
import org.json4s.jackson.JsonMethods._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemContext
import uk.gov.homeoffice.amazon.sqs.subscription.Protocol.ProcessingError
import uk.gov.homeoffice.amazon.sqs.{EmbeddedSQSServer, Message, Publisher, Queue}
import uk.gov.homeoffice.concurrent.PromiseOps
import uk.gov.homeoffice.json.JsonFormats

class SubscriberActorSpec(implicit ev: ExecutionEnv) extends Specification with JsonFormats with PromiseOps {
  trait Context extends ActorSystemContext with EmbeddedSQSServer {
    val queue = create(new Queue("test-queue"))
  }

  "Subscriber (test) actor" should {
    "receive a string and process it" in new Context {
      val result = Promise[String]()

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) {
          def process(m: Message) = result <~ Future { m.content }
        }
      }

      actor.underlyingActor receive createMessage("blah")

      result.future must beEqualTo("blah").await
    }

    "reject a string" in new Context {
      val result = Promise[String]()

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) with DefaultAfterProcess {
          def process(m: Message) = result <~> Future { throw new Exception("Processing failed") }
        }
      }

      actor.underlyingActor receive createMessage("blah")

      result.future must throwAn[Exception](message = "Processing failed").await

      val errorSubscriber = new Subscriber(queue)

      def publishedErrorMessage: JValue = parse(errorSubscriber.receiveErrors.head.content)

      "Processing failed" must eventually(beEqualTo((publishedErrorMessage \ "error-message" \ "errorStackTrace" \ "errorMessage").extract[String]))
    }
  }

  "Subscriber actor with only messages" should {
    "receive a string, process it and delete it" in new Context {
      val result = Promise[String]()

      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) {
          def process(m: Message) = result <~ Future { m.content }
        }
      }

      val publisher = new Publisher(queue)
      publisher publish "blah"

      result.future must beEqualTo("blah").await
    }

    "reject a string, publish error and delete said string" in new Context {
      val result = Promise[String]()

      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) {
          def process(m: Message) = result <~> Future {
            val exception = new Exception("Processing failed")
            self ! ProcessingError(exception, m)
            throw exception
          }
        }
      }

      val publisher = new Publisher(queue)
      publisher publish "blah"

      result.future must throwAn[Exception](message = "Processing failed").await

      val errorSubscriber = new Subscriber(queue)

      def publishedErrorMessage: Boolean = Try {
        errorSubscriber.receiveErrors.size == 1
      } getOrElse false

      true must eventually(beEqualTo(publishedErrorMessage))
    }
  }
}