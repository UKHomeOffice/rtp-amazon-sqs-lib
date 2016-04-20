package uk.gov.homeoffice.amazon.sqs

/**
  * Representation of a SQS queue with an associated error queue.
  * @param queueName String Name of the queue, where the associated error queue goes by the name queueName-error
  */
class Queue(val queueName: String) {
  val errorQueueName: String = s"$queueName-error"
}