package uk.gov.homeoffice.amazon.sqs

import akka.stream.ActorMaterializer
import play.api.libs.ws.WSClient
import play.api.libs.ws.ning.NingWSClient
import uk.gov.homeoffice.akka.ActorSystemContext

trait REST {
  this: ActorSystemContext with EmbeddedSQSServer =>

  implicit val materializer = ActorMaterializer()

  implicit val wsClient: WSClient = NingWSClient()

  def params(params: (String, String)*): Map[String, Seq[String]] = params map {
    case (k, v) => k -> Seq(v)
  } toMap
}