package uk.gov.homeoffice.amazon.sqs

import java.net.URL
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.sqs.AmazonSQSClient
import grizzled.slf4j.Logging

class SQSClient(val sqsHost: URL, credentials: AWSCredentials) extends AmazonSQSClient(credentials) with Logging {
  val Host = """^(http[s]?):\/?\/?([^:\/\s]+):?(\d*).*""".r

  sqsHost.toString match {
    case Host(protocol, "localhost", port) =>
      info(s"Configuring endpoint as $protocol://localhost:$port")
      setEndpoint(s"$protocol://localhost:$port")

    case Host(protocol, host, port) if port == "" =>
      info(s"Configuring endpoint as $host")
      setEndpoint(s"$host")

    case Host(protocol, host, port) =>
      info(s"Configuring endpoint as $protocol://$host:$port")
      setEndpoint(s"$protocol://$host:$port")
  }
}