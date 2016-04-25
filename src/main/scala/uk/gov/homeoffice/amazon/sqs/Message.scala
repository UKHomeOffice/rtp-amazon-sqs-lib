package uk.gov.homeoffice.amazon.sqs

import com.amazonaws.services.sqs.model.{Message => SQSMessage}

/**
  * Essentially a wrapped around the actual SQS message to hide unnecessary details, though the underlying message can still be accessed.
  * @param sqsMessage SQSMessage The wrapped/actual SQS message.
  */
case class Message(sqsMessage: SQSMessage) {
  val messageID: MessageID = sqsMessage.getMessageId

  val content: String = sqsMessage.getBody

  override def toString: String = sqsMessage.toString
}