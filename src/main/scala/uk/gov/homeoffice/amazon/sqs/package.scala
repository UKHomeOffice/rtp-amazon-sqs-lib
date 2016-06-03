package uk.gov.homeoffice.amazon

package object sqs {
  type MessageID = String

  val `not-amazon-sqs-message` = "Not Amazon SQS Message"

  def queueUrl(queueName: String)(implicit sqsClient: SQSClient): String = s"${sqsClient.sqsHost}/$queueName"

  case object Subscribe
}