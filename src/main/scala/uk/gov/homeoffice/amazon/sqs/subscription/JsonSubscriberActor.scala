package uk.gov.homeoffice.amazon.sqs.subscription

/*
import scala.util.Try
import com.amazonaws.services.sqs.model.Message
import org.json4s.JValue
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import uk.gov.homeoffice.json.{JsonError, JsonErrorException, JsonSchema}
import uk.gov.homeoffice.process.Processor

class JsonSubscriberActor(subscriber: Subscriber, jsonSchema: JsonSchema) extends SubscriberActor(subscriber) {
  this: Processor[JValue, _] =>

  override val publishError: PartialFunction[(Throwable, Message), Any] = {
    case (JsonErrorException(jsonError), message) =>
      debug(s"Publishing error ${jsonError.error}")

      publisher publishError compact(render(
        ("error-message" -> jsonError.toJson) ~
        ("original-message" -> message.toString)
      ))
  }

  override def process(m: Message): Try[_] = {
    def toException(jsonError: JsonError) = new JsonErrorException(jsonError)

    for {
      json <- toJson(m.getBody)
      validatedJson <- jsonSchema.validate(json).badMap(toException).toTry
      processedJson <- process(json)
    } yield processedJson
  }
}*/
