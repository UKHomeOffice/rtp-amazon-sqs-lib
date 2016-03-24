package uk.gov.homeoffice.amazon.sqs

class Queue(val queueName: String) {
  val errorQueueName: String = s"$queueName-error"
}