package models.requests

import play.api.libs.json.{Reads, __}
import play.api.libs.functional.syntax.*

case class CustomerBudgetUpdateRequest(
  amountMinor: Long,
  currencyCode: String
)

object CustomerBudgetUpdateRequest {
  implicit val reads: Reads[CustomerBudgetUpdateRequest] = (
    (__ \ "amount_minor").read[Long](Reads.min[Long](0L)) and
    (__ \ "currency_code").read[String](
      Reads.minLength[String](3) keepAnd Reads.maxLength[String](3)
    )
  )(CustomerBudgetUpdateRequest.apply _)
}
