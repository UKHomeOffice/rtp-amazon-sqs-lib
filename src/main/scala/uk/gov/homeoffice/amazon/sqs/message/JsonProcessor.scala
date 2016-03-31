package uk.gov.homeoffice.amazon.sqs.message

import scala.util.{Failure, Success, Try}
import com.amazonaws.services.sqs.model.Message
import org.json4s._
import org.json4s.jackson.JsonMethods.{parse => toJson, _}
import org.scalactic.{Bad, Good, _}
import uk.gov.homeoffice.json.{JsonError, JsonSchema}

/**
  * Process JSON from Amazon SQS where the JSON has been validated against a given JSON schema
  * @tparam R The Result of processing a message
  */
trait JsonProcessor[R] extends MessageProcessor[R] {
  def jsonSchema: JsonSchema

  def process(json: JValue): Try[R]

  final def process(message: Message): R Or ErrorMessage = processWith(process)(message)

  def processWith(process: JValue => Try[R])(m: Message): R Or ErrorMessage = (for {
    json <- parse(m.getBody)
    validatedJson <- jsonSchema.validate(json).badMap(toException).toTry
    processedJson <- process(json)
  } yield processedJson) match {
    case Success(r) => Good(r)
    case Failure(t) => Bad(t.getMessage)
  }

  def parse(s: String): Try[JValue] = Try {
    toJson(s)
  } recoverWith {
    case t: Throwable => Failure(new Exception(pretty(render(JsonError(error = Some("Invalid JSON format"), throwable = Some(t)).toJson))))
  }

  def toException(jsonError: JsonError): Exception = new Exception(pretty(render(jsonError.toJson)))
}