package uk.gov.homeoffice.amazon.sqs

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}
import akka.testkit.TestActorRef
import com.amazonaws.services.sqs.model.Message
import org.json4s.jackson.JsonMethods._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import uk.gov.homeoffice.akka.ActorSystemContext
import uk.gov.homeoffice.amazon.sqs.subscription.{StringSubscriberActor, Subscriber}
import uk.gov.homeoffice.json.JsonFormats
import uk.gov.homeoffice.process.Processor

class StringSubscriberActorSpec(implicit ev: ExecutionEnv) extends Specification with JsonFormats {
  def promised[R](result: Promise[R], processed: R) = {
    result success processed
    processed
  }

  "String subscriber actor" should {
    "receive a string and process it" in new ActorSystemContext with EmbeddedSQSServer {
      val input = "blah"
      val result = Promise[Try[Int]]()

      trait StringToIntProcessor extends Processor[String, Int] {
        override def process(in: String): Try[Int] = promised(result, Success(77))
      }

      val actor = TestActorRef {
        new StringSubscriberActor(new Subscriber(create(new Queue("test-queue")))) with StringToIntProcessor
      }

      actor.underlyingActor receive createMessage(input)

      result.future must beEqualTo(Success(77)).await
    }

    "reject a string" in new ActorSystemContext with EmbeddedSQSServer {
      val input = "blah"
      val result = Promise[Try[Int]]()

      trait StringToIntProcessor extends Processor[String, Int] {
        override def process(in: String): Try[Int] = promised(result, Failure(new Exception(input)))
      }

      val queue = create(new Queue("test-queue"))

      val actor = TestActorRef {
        new StringSubscriberActor(new Subscriber(create(new Queue("test-queue")))) with StringToIntProcessor
      }

      actor.underlyingActor receive createMessage(input)

      result.future must beLike[Try[Int]] {
        case Failure(t) => t.getMessage mustEqual input
      }.await

      val errorSubscriber = new Subscriber(queue)

      errorSubscriber.receiveErrors must beLike {
        case Seq(m: Message) =>
          (parse(m.getBody) \ "error-message" \ "errorStackTrace" \ "errorMessage").extract[String] mustEqual input
      }
    }
  }
}