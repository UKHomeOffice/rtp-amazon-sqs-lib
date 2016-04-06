package uk.gov.homeoffice.amazon.sqs.subscription

import scala.collection.JavaConversions._
import com.amazonaws.services.sqs.model.Message
import uk.gov.homeoffice.amazon.sqs._

class Subscriber(val queue: Queue)(implicit val sqsClient: SQSClient) extends QueueCreation {
  create(queue)

  def receive: Seq[Message] = receive(queue.queueName)

  def receiveErrors: Seq[Message] = receive(queue.errorQueueName)

  private def receive(queueName: String): Seq[Message] = sqsClient.receiveMessage(queueUrl(queueName)).getMessages
}