package uk.gov.homeoffice.amazon.sqs

import scala.collection.JavaConversions._
import com.amazonaws.services.sqs.model.Message

class Subscriber(val queue: Queue)(implicit val sqsClient: SQSClient) extends QueueCreation {
  create(queue)

  def receive: Seq[Message] = receive(queue.queueName)

  def receiveErrors: Seq[Message] = receive(queue.errorQueueName)

  private def receive(queueName: String): Seq[Message] = sqsClient.receiveMessage(queueUrl(queueName)).getMessages
}