package models.requests

import play.api.libs.json.{Reads, __}
import play.api.libs.functional.syntax.*

case class CustomerCreateRequest(
  email: String,
  currencyCode: String
)

object CustomerCreateRequest {
  implicit val reads: Reads[CustomerCreateRequest] = (
    (__ \ "email").read[String](Reads.minLength[String](1)) and
    (__ \ "currency_code").read[String](
      Reads.minLength[String](3) keepAnd Reads.maxLength[String](3)
    )
  )(CustomerCreateRequest.apply _)
}
