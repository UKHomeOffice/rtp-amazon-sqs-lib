package uk.gov.homeoffice.amazon.sqs

import java.net.URL
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.sqs.AmazonSQSClient

class SQSClient(val sqsHost: URL, credentials: AWSCredentials) extends AmazonSQSClient(credentials) {
  val Host = """^(http[s]?:\/?\/?[^:\/\s]+):?(\d*).*""".r

  sqsHost.toString match {
    case Host(h, p) if p == "" => setEndpoint(h)
    case Host(h, p) => setEndpoint(s"$h:$p")
  }
}