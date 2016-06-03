package uk.gov.homeoffice.amazon.sqs

import java.net.URL
import java.util.UUID
import com.amazonaws.auth.BasicAWSCredentials
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.Scope
import de.flapdoodle.embed.process.runtime.Network._
import uk.gov.homeoffice.amazon.sqs.subscription.Subscriber
import uk.gov.homeoffice.specs2.ComposableAround

trait EmbeddedSQSServer extends SQSServer with QueueCreation with Scope with ComposableAround {
  val sqsHost = new URL(s"http://localhost:$getFreeServerPort")

  val server = SQSRestServerBuilder withInterface sqsHost.getHost withPort sqsHost.getPort start()

  implicit val sqsClient = new SQSClient(new URL(s"$sqsHost/queue"), new BasicAWSCredentials("x", "x"))

  val createMessage: String => Message =
    message => {
      val queue = create(new Queue(UUID.randomUUID().toString))
      val publisher = new Publisher(queue)
      val subscriber = new Subscriber(queue)

      publisher publish message
      subscriber.receive.head
    }

  override def around[R: AsResult](r: => R): Result = try {
    server waitUntilStarted()
    info(s"Started SQS $sqsHost")
    super.around(r)
  } finally {
    info(s"Stopping SQS $sqsHost")
    server stopAndWait()
  }
}