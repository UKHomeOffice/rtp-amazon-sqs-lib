package uk.gov.homeoffice.amazon.sqs.subscription

import uk.gov.homeoffice.amazon.sqs.Message

object Protocol {
  case class Processed(message: Message)

  case class ProcessingError(throwable: Throwable, message: Message)
}