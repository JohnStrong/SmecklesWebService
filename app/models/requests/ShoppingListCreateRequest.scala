package models.requests

import models.ShoppingListItem
import play.api.libs.json.{Json, JsonValidationError, Reads, __}
import play.api.libs.functional.syntax.*

import java.time.LocalDate

case class ShoppingListCreateRequest(
  name: String,
  periodStart: LocalDate,
  dayDate: LocalDate,
  items: List[ShoppingListItem]
)

object ShoppingListCreateRequest {
  implicit val reads: Reads[ShoppingListCreateRequest] = (
    (__ \ "name").read[String](
      Reads.minLength[String](1) keepAnd Reads.maxLength[String](20)
    ) and
    (__ \ "period_start").read[LocalDate] and
    (__ \ "day_date").read[LocalDate] and
    (__ \ "items").read[List[ShoppingListItem]](
      Reads.minLength[List[ShoppingListItem]](1) keepAnd Reads.maxLength[List[ShoppingListItem]](50)
    )
  )(ShoppingListCreateRequest.apply _)
    .filter(JsonValidationError("period_start must be the 1st of the month"))(_.periodStart.getDayOfMonth == 1)
    .filter(JsonValidationError("day_date must be within the same month as period_start")) { req =>
      req.dayDate.getYear == req.periodStart.getYear &&
      req.dayDate.getMonth == req.periodStart.getMonth
    }
}
