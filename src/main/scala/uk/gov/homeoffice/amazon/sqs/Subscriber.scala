package uk.gov.homeoffice.amazon.sqs

import scala.collection.JavaConversions._
import com.amazonaws.services.sqs.model.Message

class Subscriber(queue: Queue)(implicit sqsClient: SQSClient) {
  def receive: Seq[Message] = sqsClient.receiveMessage(s"${sqsClient.sqsHost}/queue/${queue.queueName}").getMessages
}