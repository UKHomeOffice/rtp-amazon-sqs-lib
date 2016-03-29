package uk.gov.homeoffice.amazon

package object sqs {
  type MessageID = String

  def queueUrl(queueName: String)(implicit sqsClient: SQSClient): String = s"${sqsClient.sqsHost}/queue/$queueName"
}