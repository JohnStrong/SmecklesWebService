package models.requests

import play.api.libs.json.{Reads, __}
import play.api.libs.functional.syntax.*

case class CustomerBudgetUpdateRequest(
  amountMinor: Long
)

object CustomerBudgetUpdateRequest {
  implicit val reads: Reads[CustomerBudgetUpdateRequest] =
    (__ \ "amount_minor").read[Long](Reads.min[Long](0L))
      .map(CustomerBudgetUpdateRequest.apply)
}
