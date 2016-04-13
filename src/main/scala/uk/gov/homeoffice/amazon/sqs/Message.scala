package uk.gov.homeoffice.amazon.sqs

import com.amazonaws.services.sqs.model.{Message => SQSMessage}

class Message(val sqsMessage: SQSMessage) {
  val messageID = sqsMessage.getMessageId

  val content = sqsMessage.getBody

  override def toString: MessageID = sqsMessage.toString
}