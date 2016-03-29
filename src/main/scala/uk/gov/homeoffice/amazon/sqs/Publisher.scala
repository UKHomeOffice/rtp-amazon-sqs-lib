package uk.gov.homeoffice.amazon.sqs

import com.amazonaws.services.sqs.model.SendMessageResult

class Publisher(val queue: Queue)(implicit val sqsClient: SQSClient) {
  def publish(message: String): MessageID = publish(message, queue.queueName).getMessageId

  def publishError(message: String): MessageID = publish(message, queue.errorQueueName).getMessageId

  private def publish(message: String, queueName: String): SendMessageResult = sqsClient.sendMessage(s"${sqsClient.sqsHost}/queue/$queueName", message)
}