package uk.gov.homeoffice.amazon.sqs

import grizzled.slf4j.Logging

trait QueueCreation extends Logging {
  this: { val sqsClient: SQSClient } =>

  def create(queue: Queue): Queue = {
    def createQueue(queueName: String) = try {
      sqsClient createQueue queueName
    } catch {
      case t: Throwable => warn(t.getMessage)
    }

    createQueue(queue.queueName)
    createQueue(queue.errorQueueName)
    queue
  }
}