package uk.gov.homeoffice.amazon.sqs

trait QueueCreation {
  this: SQSClient with Queue =>

  def createQueue[R](f: => R) = {
    sqsClient.createQueue(queueName)
    sqsClient.createQueue(errorQueueName)
    f
  }
}