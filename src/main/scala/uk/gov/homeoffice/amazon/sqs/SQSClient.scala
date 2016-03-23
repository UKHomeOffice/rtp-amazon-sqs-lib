package uk.gov.homeoffice.amazon.sqs

import java.net.URL
import com.amazonaws.services.sqs.AmazonSQSClient

trait SQSClient {
  def sqsHost: URL

  def sqsClient: AmazonSQSClient
}