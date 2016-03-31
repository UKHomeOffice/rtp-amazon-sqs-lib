package uk.gov.homeoffice.amazon.sqs.message

import com.amazonaws.services.sqs.model.Message
import org.scalactic._

/**
  * Process a Message from Amazon SQS
  * @tparam R The Result of processing a message
  */
trait MessageProcessor[R] {
  def process(message: Message): R Or ErrorMessage
}