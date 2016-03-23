package uk.gov.homeoffice.amazon.sqs

import java.net.URL
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sqs.AmazonSQSClient
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.Scope
import de.flapdoodle.embed.process.runtime.Network._
import uk.gov.homeoffice.specs2.ComposableAround

trait SQSTestServer extends SQSServer with Scope with ComposableAround {
  server =>

  val sqsHost = new URL(s"http://localhost:$getFreeServerPort")

  override def around[R: AsResult](r: => R): Result = {
    val server = SQSRestServerBuilder.withInterface(sqsHost.getHost).withPort(sqsHost.getPort).start()

    try {
      server.waitUntilStarted()
      super.around(r)
    } finally {
      server.stopAndWait()
    }
  }

  trait SQSTestClient extends SQSClient {
    val sqsHost = server.sqsHost

    val sqsClient = new AmazonSQSClient(new BasicAWSCredentials("x", "x"))
    sqsClient.setEndpoint(sqsHost.toString)
  }

  trait SQSTestQueue extends Queue {
    this: SQSTestClient =>

    val queueName = "test-queue"

    sqsClient.createQueue(queueName)
  }
}