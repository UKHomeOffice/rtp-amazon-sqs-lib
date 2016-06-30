package uk.gov.homeoffice.amazon.sqs.subscription

import java.security.MessageDigest

import org.json4s.jackson.JsonMethods._
import uk.gov.homeoffice.amazon.sqs.Message
import uk.gov.homeoffice.crypt.{Crypto, Secrets}

import scala.util.Success


class CryptoFilter(implicit secrets: Secrets) extends (Message => Option[Message]) with Crypto {
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

    case _ => None
  }
}
