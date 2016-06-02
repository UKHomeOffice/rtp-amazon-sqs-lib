package uk.gov.homeoffice.amazon.sqs

import java.net.URL
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.sqs.AmazonSQSClient

class SQSClient(val sqsHost: URL, credentials: AWSCredentials) extends AmazonSQSClient(credentials) {
  val Host = """^http[s]?:\/?\/?([^:\/\s]+)\/?\w*\/?""".r

  sqsHost.toString match {
    case Host(h) => setEndpoint(h)
  }
}