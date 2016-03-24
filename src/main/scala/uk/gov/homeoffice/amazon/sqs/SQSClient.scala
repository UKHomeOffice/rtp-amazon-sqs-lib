package uk.gov.homeoffice.amazon.sqs

import java.net.URL
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.sqs.AmazonSQSClient

class SQSClient(val sqsHost: URL, credentials: AWSCredentials) extends AmazonSQSClient(credentials) {
  setEndpoint(sqsHost.toString)
}