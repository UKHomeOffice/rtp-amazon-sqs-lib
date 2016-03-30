package uk.gov.homeoffice.amazon.sqs.message

import com.amazonaws.services.sqs.model.Message
import org.scalactic._

trait MessageProcessor[Result] {
  def process(message: Message): Result Or ErrorMessage
}