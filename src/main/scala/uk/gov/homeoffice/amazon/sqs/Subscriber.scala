package uk.gov.homeoffice.amazon.sqs

import scala.collection.JavaConversions._
import com.amazonaws.services.sqs.model.Message

trait Subscriber extends QueueCreation {
  this: SQSClient with Queue =>

  def receive: Seq[Message] = createQueue {
    sqsClient.receiveMessage(s"$sqsHost/queue/$queueName").getMessages
  }
}