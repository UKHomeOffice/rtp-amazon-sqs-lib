package uk.gov.homeoffice.amazon.sqs.subscription

import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.testkit.TestActorRef
import org.json4s.JValue
import org.json4s.jackson.JsonMethods._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.{ActorExpectations, ActorSystemContext}
import uk.gov.homeoffice.amazon.sqs._
import uk.gov.homeoffice.amazon.sqs.subscription.protocol.Processed
import uk.gov.homeoffice.concurrent.PromiseOps
import uk.gov.homeoffice.json.JsonFormats

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Try

class SubscriberActorSpec(implicit ev: ExecutionEnv) extends Specification with JsonFormats with PromiseOps {
  trait Context extends ActorSystemContext with ActorExpectations with EmbeddedSQSServer {
    implicit val listeners = Seq(testActor)

    val queue = create(new Queue("test-queue"))
  }

  "Subscriber actor" should {
    "receive a string and process it" in new Context {
      val result = Promise[String]()

      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) {
          def receive: Receive = {
            case m: Message => result <~ Future { m.content }
          }
        }
      }

      actor ! createMessage("blah")

      result.future must beEqualTo("blah").await
    }

    "reject a string" in new Context {
      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue)) {
          def receive: Receive = {
            case m: Message => publishError(new Exception("Processing failed"), m)
          }
        }
      }

      actor ! createMessage("blah")

      val errorSubscriber = new Subscriber(queue)

      def publishedErrorMessage: JValue = parse(errorSubscriber.receiveErrors.head.content)

      "Processing failed" must eventually(beEqualTo((publishedErrorMessage \ "error-message" \ "errorStackTrace" \ "errorMessage").extract[String]))
    }

    "reject a message as it didn't pass through the filter" in new Context {
      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue), rejectFilter) {
          def receive: Receive = {
            case m: Message => sender() ! Processed(m)
          }
        }
      }

      def rejectFilter(m: Message): Option[Message] = None

      actor ! createMessage("blah")

      expectMsgType[Message]
      expectNoMsg(3 seconds)
    }

    "let the message pass through the filter" in new Context {
      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue), acceptFilter) {
          def receive: Receive = {
            case m: Message =>
              sender() ! Processed(m)
          }
        }
      }

      def acceptFilter(m: Message): Option[Message] = Some(m)

      val message = createMessage("blah")
      actor ! message

      eventuallyExpectMsg[Processed] {
        case Processed(m) => m == message
      }
    }


    "let the message pass through two filters" in new Context {
      val actor = TestActorRef {
        new SubscriberActor(new Subscriber(queue), acceptFilter, acceptFilter) {
          def receive: Receive = {
            case m: Message =>
              sender() ! Processed(m)
          }
        }
      }

      def acceptFilter(m: Message): Option[Message] = Some(m)

      val message = createMessage("blah")
      actor ! message

      eventuallyExpectMsg[Processed] {
        case Processed(m) => m == message
      }
    }

  }

  "Subscriber actor with only messages" should {
    "receive a string, process it and delete it" in new Context {
      val result = Promise[String]()

      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) {
          def receive: Receive = {
            case m: Message => result <~ Future { m.content }
          }
        }
      }

      val publisher = new Publisher(queue)
      publisher publish "blah"

      result.future must beEqualTo("blah").awaitFor(5 seconds)
    }

    "reject a string, publish error and delete said string" in new Context {
      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) {
          def receive: Receive = {
            case m: Message => publishError(new Exception("Processing failed"), m)
          }
        }
      }

      val publisher = new Publisher(queue)
      publisher publish "blah"

      val errorSubscriber = new Subscriber(queue)

      def publishedErrorMessage: Boolean = Try {
        errorSubscriber.receiveErrors.size == 1
      } getOrElse false

      true must eventually(beEqualTo(publishedErrorMessage))
    }
  }

  "Subscriber actor with only messages and a message protocol" should {
    "receive a string, fire a message indicating that the message has been processed" in new Context {
      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) {
          def receive: Receive = {
            case m: Message => sender() ! Processed(m)
          }
        }
      }

      val message = "blah"

      val publisher = new Publisher(queue)
      publisher publish message

      eventuallyExpectMsg[Processed] {
        case Processed(m) => m.content == message
      }
    }

    "throw an exception for first message, then receive a string, fire a message indicating that the message has been processed" in new Context {
      system actorOf Props {
        new SubscriberActor(new Subscriber(queue)) {
          def receive: Receive = {
            case m: Message if m.content == "Crash" => throw new Exception("Crash")
            case m: Message => sender() ! Processed(m)
          }
        }
      }

      val publisher = new Publisher(queue)

      publisher publish "Crash"

      val message = "blah"
      publisher publish message

      eventuallyExpectMsg[Processed] {
        case Processed(m) => m.content == message
      }
    }
  }
}