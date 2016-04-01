package uk.gov.homeoffice.amazon.sqs

import java.net.URL
import scala.util.Success
import akka.actor.{ActorSystem, Props}
import com.amazonaws.auth.BasicAWSCredentials
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import uk.gov.homeoffice.amazon.sqs.message.JsonProcessor
import uk.gov.homeoffice.json.JsonSchema
import uk.gov.homeoffice.system.Exit

/**
  * Example of booting an application to publish/subscribe to an Amazon SQS instance.
  * <pre>
  * 1) Start up an instance of ElasticMQ (to run an instance of Amazon SQS locally) - From the root of this project:
  *    java -jar elasticmq-server-0.9.0.jar
  *    OR with a configuration to e.g. set up queues
  *    java -Dconfig.file=src/test/resources/application.test.conf -jar elasticmq-server-0.9.0.jar
  *    which starts up a working server that binds to localhost:9324
  * 2) Boot this application:
  *    sbt test:run
  * </pre>
  */
object ExampleBoot extends App {
  val system = ActorSystem("amazon-sqs-actor-system")

  implicit val sqsClient = new SQSClient(new URL("http://localhost:9324"), new BasicAWSCredentials("x", "x"))

  val queue = new Queue("test-queue")

  system actorOf Props {
    new SubscriberActor(new Subscriber(queue)) with JsonToStringProcessor
  }

  new Publisher(queue) publish compact(render("input" -> "blah"))
}

trait JsonToStringProcessor extends JsonProcessor[String] with Exit {
  val jsonSchema = JsonSchema(
    ("id" -> "http://www.bad.com/schema") ~
    ("$schema" -> "http://json-schema.org/draft-04/schema") ~
    ("type" -> "object") ~
    ("properties" ->
      ("input" ->
        ("type" -> "string")))
  )

  def process(json: JValue) = exitAfter {
    val result = Success("Well Done!")
    println(result)
    result
  }
}