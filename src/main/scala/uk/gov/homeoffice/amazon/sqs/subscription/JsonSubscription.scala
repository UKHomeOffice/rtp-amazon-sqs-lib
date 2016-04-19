package uk.gov.homeoffice.amazon.sqs.subscription

import scala.util.{Failure, Success}
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalactic.{Bad, Or}
import uk.gov.homeoffice.amazon.sqs.Message
import uk.gov.homeoffice.json.{JsonError, JsonSchema}

trait JsonSubscription {
  this: SubscriberActor =>

  /**
    * Parse a given Message to JSON using the given JSON Schema
    * @param m Message To be parsed to JSON
    * @param js JsonSchema to validate parsed JSON
    * @return JValue Or JsonError According to the outcome of parsing the given Message
    */
  def parse(m: Message, js: JsonSchema): JValue Or JsonError = (toJson(m.content) match {
    case Success(json) => js validate json
    case Failure(t) => Bad(JsonError(error = Some("Failed to parse given message to JSON"), throwable = Some(t)))
  }) badMap { jsonError =>
    publishError(jsonError, m)
  }

  /**
    * Error publication of SubscriberActor is overridden to handle JSON errors.
    */
  def publishError(jsonError: JsonError, message: Message): JsonError = {
    debug(s"Publishing error ${jsonError.error}")

    publisher publishError compact(render(
      ("error-message" -> jsonError.toJson) ~
      ("original-message" -> message.toString)
    ))

    delete(message)
    jsonError
  }
}