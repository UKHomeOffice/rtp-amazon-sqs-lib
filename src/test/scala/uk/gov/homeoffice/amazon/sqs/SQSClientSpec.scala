package uk.gov.homeoffice.amazon.sqs

import java.net.{URI, URL}
import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.auth.AWSCredentials
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class SQSClientSpec extends Specification with Mockito {
  "SQS client" should {
    "be instantiated without account number" in {
      val sqsClient = new SQSClient(new URL("https://sqs.eu-west-1.amazonaws.com"), mock[AWSCredentials])

      val endpointField = classOf[AmazonWebServiceClient].getDeclaredField("endpoint")
      endpointField.setAccessible(true)

      endpointField.get(sqsClient) mustEqual new URI("https://sqs.eu-west-1.amazonaws.com")
    }

    "be instantiated with account number" in {
      val sqsClient = new SQSClient(new URL("https://sqs.eu-west-1.amazonaws.com/1111"), mock[AWSCredentials])

      val endpointField = classOf[AmazonWebServiceClient].getDeclaredField("endpoint")
      endpointField.setAccessible(true)

      endpointField.get(sqsClient) mustEqual new URI("https://sqs.eu-west-1.amazonaws.com")
    }
  }
}