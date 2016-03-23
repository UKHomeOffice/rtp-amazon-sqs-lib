package uk.gov.homeoffice.amazon.sqs

trait Queue {
  def queueName: String

  lazy val errorQueueName: String = s"$queueName-error"
}