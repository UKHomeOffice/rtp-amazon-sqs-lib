package uk.gov.homeoffice.amazon.sqs

import scala.util.Try
import org.json4s._
import uk.gov.homeoffice.json.Json._
import uk.gov.homeoffice.json.{JsonError, JsonErrorException, JsonSchema}

package object json {
  def using[R](s: JsonSchema)(process: JValue => Try[R]) = (m: Message) => {
    def toException(jsonError: JsonError) = new JsonErrorException(jsonError)

    for {
      json <- toJson(m.content)
      validatedJson <- s.validate(json).badMap(toException).toTry
      processedJson <- process(json)
    } yield processedJson
  }
}