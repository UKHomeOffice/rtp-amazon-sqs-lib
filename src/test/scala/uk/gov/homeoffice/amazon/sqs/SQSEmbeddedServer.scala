package uk.gov.homeoffice.amazon.sqs

import java.net.URL
import java.util.UUID
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sqs.model.Message
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.Scope
import de.flapdoodle.embed.process.runtime.Network._
import uk.gov.homeoffice.specs2.ComposableAround

trait SQSEmbeddedServer extends SQSServer with QueueCreation with Scope with ComposableAround {
  val sqsHost = new URL(s"http://localhost:$getFreeServerPort")

  implicit val sqsClient = new SQSClient(sqsHost, new BasicAWSCredentials("x", "x"))

  val createMessage: String => Message =
    message => {
      val queue = create(new Queue(UUID.randomUUID().toString))
      val publisher = new Publisher(queue)
      val subscriber = new Subscriber(queue)

      publisher publish message
      subscriber.receive.head
    }

  override def around[R: AsResult](r: => R): Result = {
    val server = SQSRestServerBuilder.withInterface(sqsHost.getHost).withPort(sqsHost.getPort).start()

    try {
      server.waitUntilStarted()
      super.around(r)
    } finally {
      server.stopAndWait()
    }
  }
}