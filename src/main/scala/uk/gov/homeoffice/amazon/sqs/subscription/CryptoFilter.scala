package uk.gov.homeoffice.amazon.sqs.subscription

import java.security.MessageDigest
import scala.util.{Failure, Success}
import org.json4s.jackson.JsonMethods._
import grizzled.slf4j.Logging
import uk.gov.homeoffice.amazon.sqs.Message
import uk.gov.homeoffice.crypt.{Crypto, Secrets}

class CryptoFilter(implicit secrets: Secrets) extends (Message => Option[Message]) with Crypto with Logging {
  def apply(msg: Message): Option[Message] = decrypt(parse(msg.content)) match {
    case Success(a) =>
      val sqsMessage = new com.amazonaws.services.sqs.model.Message()
      sqsMessage.setAttributes(msg.sqsMessage.getAttributes)
      sqsMessage.setBody(a)
      sqsMessage.setMD5OfBody(new String(MessageDigest.getInstance("MD5").digest(a.getBytes)))
      sqsMessage.setMessageId(msg.messageID)
      sqsMessage.setMD5OfMessageAttributes(msg.sqsMessage.getMD5OfMessageAttributes)
      sqsMessage.setReceiptHandle(msg.sqsMessage.getReceiptHandle)
      Some(Message(sqsMessage))

    case Failure(t) =>
      warn(s"Failed to decrypt message because of: ${t.getMessage}, where given message was: $msg")
      None
  }
}