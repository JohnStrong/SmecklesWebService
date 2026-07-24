package models.requests

import play.api.libs.json.{Json, JsonValidationError, Reads, __}
import play.api.libs.functional.syntax.*

import java.time.LocalDate

case class CustomerBudgetCreateRequest(
  periodStart: LocalDate,
  periodEnd: LocalDate,
  amountMinor: Long
)

object CustomerBudgetCreateRequest {
  implicit val reads: Reads[CustomerBudgetCreateRequest] = (
    (__ \ "period_start").read[LocalDate] and
    (__ \ "period_end").read[LocalDate] and
    (__ \ "amount_minor").read[Long](Reads.min[Long](0L))
  )(CustomerBudgetCreateRequest.apply _)
    .filter(JsonValidationError("period_start must be before period_end")) { req =>
      req.periodStart.isBefore(req.periodEnd)
    }
}
