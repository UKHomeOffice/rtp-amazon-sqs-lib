package uk.gov.homeoffice.amazon.sqs.subscription

import scala.collection.JavaConversions._
import uk.gov.homeoffice.amazon.sqs._

class Subscriber(val queue: Queue)(implicit val sqsClient: SQSClient) extends QueueCreation {
  create(queue)

  def receive: Seq[Message] = receive(queue.queueName)

  def receiveErrors: Seq[Message] = receive(queue.errorQueueName)

  private def receive(queueName: String): Seq[Message] = try {
    val messages = sqsClient.receiveMessage(queueUrl(queueName)).getMessages.map(new Message(_)).toList

    messages.size match {
      case 0 => trace("Received 0 messages")
      case 1 => info(s"Received 1 message")
      case x => info(s"Received $x messages")
    }

    messages

  } catch {
    case t: Throwable =>
      t.printStackTrace()
      Nil
  }
}