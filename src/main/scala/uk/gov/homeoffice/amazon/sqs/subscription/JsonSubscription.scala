package uk.gov.homeoffice.amazon.sqs.subscription

import scala.util.{Failure, Success, Try}
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalactic.{Bad, Good, Or}
import uk.gov.homeoffice.amazon.sqs.Message
import uk.gov.homeoffice.json.{JsonError, JsonErrorException, JsonSchema}

trait JsonSubscription {
  this: SubscriberActor =>

  /** JSON schema to be provided that this trait will use to validate JSON that was received via subscription and validated before processing said JSON. */
  //val jsonSchema: JsonSchema

  /**
    * Process functionality to be provided, which will process JSON that was received via subscription and validated against the declared JSON schema.
    * @param json JValue Validated JSON (against declared JSON schema) to be processed
    * @return Result of processing wrapped in a Try to account for possible exceptions
    */
  //def process(json: JValue): Try[_]

  /** Error publication of SubscriberActor is overridden to handle JSON errors. */
  /*override val publishError: PartialFunction[(Throwable, Message), Any] = {
    case (JsonErrorException(jsonError), message) =>
      debug(s"Publishing error ${jsonError.error}")

      publisher publishError compact(render(
        ("error-message" -> jsonError.toJson) ~
        ("original-message" -> message.toString)
      ))
  }*/

  /**
    * Message processing of SubscriberActor is overridden to convert a subscribed message into JSON and validated before provided processing.
    * @param m Message received via subscription
    * @return Result of processing wrapped in a Try to account for possible exceptions.
    */
  /*override def process(m: Message): Try[_] = {
    def toException(jsonError: JsonError) = new JsonErrorException(jsonError)

    for {
      json <- toJson(m.content)
      validatedJson <- jsonSchema.validate(json).badMap(toException).toTry
      processedJson <- process(json)
    } yield processedJson
  }*/

  /*def parse(m: Message, js: JsonSchema): JValue Or JsonError = toJson(m.content) match {
    case Success(json) => js validate json
    case Failure(t) => Bad(JsonError(error = Some(s"Failed to parse to JSON $m, with exception: ${t.getMessage}"), throwable = Some(t)))
  }*/

  /*def parse(m: Message, js: JsonSchema): JValue = toJson(m.content) match {
    case Success(json) => js validate json match {
      case Good(validatedJson) => validatedJson
      case Bad(jsonError) => throw new Exception(s"Failed to validate JSON $json, because of error: $jsonError")
    }

    case Failure(t) =>
      throw new Exception(s"Failed to parse given message to JSON, because of error: ${t.getMessage}", t)
  }*/

  def publishError(jsonError: JsonError, message: Message): JsonError = {
    debug(s"Publishing error ${jsonError.error}")

    publisher publishError compact(render(
      ("error-message" -> jsonError.toJson) ~
      ("original-message" -> message.toString)
    ))

    jsonError
  }

  /*def parse(m: Message, js: JsonSchema): Try[JValue] = for {
    json <- toJson(m.content)
    validatedJson <- js.validate(json).badMap(_.toException).toTry
  } yield validatedJson*/

  def parse(m: Message, js: JsonSchema): JValue Or JsonError = (toJson(m.content) match {
    case Success(json) => js validate json
    case Failure(t) => Bad(JsonError(error = Some("Failed to parse given message to JSON"), throwable = Some(t)))
  }) badMap { jsonError =>
    publishError(jsonError, m)
  }
}