package uk.gov.homeoffice.amazon.sqs

class Publisher(queue: Queue)(implicit sqsClient: SQSClient) {
  def publish(message: => String) = sqsClient.sendMessage(s"${sqsClient.sqsHost}/queue/${queue.queueName}", message)
}