package uk.gov.homeoffice.amazon.sqs.subscription

import scala.util.{Failure, Success, Try}
import uk.gov.homeoffice.amazon.sqs.Message

/**
  * Once a subscribed message has been processed, extra functionality can be run @see DefaultAfterProcess
  */
trait AfterProcess {
  this: SubscriberActor =>

  def afterProcess[R](message: Message): PartialFunction[Try[R], _]
}

/**
  * Default after process will delete a message and if necessary publish an error.
  */
trait DefaultAfterProcess extends AfterProcess {
  this: SubscriberActor =>

  /**
    * After processing that will delete a message upon processing (success or failure) and publish any error encountered.
    * @param message Message being processed.
    * @tparam R The type of result from processing
    * @return PartialFunction[Try[R], _]
    */
  override def afterProcess[R](message: Message): PartialFunction[Try[R], _] = {
    case Success(r) =>
      debug(s"Processed message: $message")
      delete(message)
      r

    case Failure(t) =>
      debug(s"Failed to process message: $message")
      publishError(t, message)
      throw t
  }
}