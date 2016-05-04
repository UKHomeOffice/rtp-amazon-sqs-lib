package uk.gov.homeoffice.amazon.sqs.subscription

import uk.gov.homeoffice.amazon.sqs.Message

package object protocol {

  sealed trait Protocol

  case class Processed(message: Message) extends Protocol

  case class ProcessingError(throwable: Throwable, message: Message) extends Protocol
}