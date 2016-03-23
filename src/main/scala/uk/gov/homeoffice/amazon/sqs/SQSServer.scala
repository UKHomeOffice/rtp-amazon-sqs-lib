package uk.gov.homeoffice.amazon.sqs

import java.net.URL

trait SQSServer {
  def sqsHost: URL
}