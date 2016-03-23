package uk.gov.homeoffice.amazon.sqs

trait Publisher extends QueueCreation {
  this: SQSClient with Queue =>

  def publish(message: => String) = createQueue {
    sqsClient.sendMessage(s"$sqsHost/queue/$queueName", message)
  }
}