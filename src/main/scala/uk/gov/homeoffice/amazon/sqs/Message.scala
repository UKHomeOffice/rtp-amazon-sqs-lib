package uk.gov.homeoffice.amazon.sqs

import com.amazonaws.services.sqs.model.{Message => SQSMessage}

class Message(val sqsMessage: SQSMessage) {
  val messageID: MessageID = sqsMessage.getMessageId

  val content: String = sqsMessage.getBody

  override def toString: String = sqsMessage.toString
}