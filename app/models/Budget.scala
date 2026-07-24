package models

import play.api.libs.json.*
import play.api.libs.functional.syntax._
import java.time.LocalDate

case class Budget(
   email: String,
   periodStart: LocalDate,
   periodEnd: LocalDate,
   amountMinor: Long
)

object Budget {
  implicit val writes: Writes[Budget] = (
    (__ \ "email").write[String] and
    (__ \ "period_start").write[LocalDate] and
    (__ \ "period_end").write[LocalDate] and
    (__ \ "amount_minor").write[Long]
  )(b => (b.email, b.periodStart, b.periodEnd, b.amountMinor))
}
