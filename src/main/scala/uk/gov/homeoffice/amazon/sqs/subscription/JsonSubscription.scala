package uk.gov.homeoffice.amazon.sqs.subscription

import scala.util.Try
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import uk.gov.homeoffice.amazon.sqs.Message
import uk.gov.homeoffice.json.{JsonError, JsonErrorException, JsonSchema}

trait JsonSubscription {
  this: SubscriberActor =>

  /** JSON schema to be provided that this trait will use to validate JSON that was received via subscription and validated before processing said JSON. */
  val jsonSchema: JsonSchema

  /**
    * Process functionality to be provided, which will process JSON that was received via subscription and validated against the declared JSON schema.
    * @param json JValue Validated JSON (against declared JSON schema) to be processed
    * @return Result of processing wrapped in a Try to account for possible exceptions
    */
  def process(json: JValue): Try[_]

  /** Error publication of SubscriberActor is overridden to handle JSON errors. */
  override val publishError: PartialFunction[(Throwable, Message), Any] = {
    case (JsonErrorException(jsonError), message) =>
      debug(s"Publishing error ${jsonError.error}")

      publisher publishError compact(render(
        ("error-message" -> jsonError.toJson) ~
        ("original-message" -> message.toString)
      ))
  }

  /**
    * Message processing of SubscriberActor is overridden to convert a subscribed message into JSON and validated before provided processing.
    * @param m Message received via subscription
    * @return Result of processing wrapped in a Try to account for possible exceptions.
    */
  override def process(m: Message): Try[_] = {
    def toException(jsonError: JsonError) = new JsonErrorException(jsonError)

    for {
      json <- toJson(m.content)
      validatedJson <- jsonSchema.validate(json).badMap(toException).toTry
      processedJson <- process(json)
    } yield processedJson
  }
}