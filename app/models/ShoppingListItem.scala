package models

import play.api.libs.json.*
import play.api.libs.functional.syntax._

case class ShoppingListItem(
  quantity: Int,
  currencyCode: String,
  unitAmountMinor: Long,
  lineAmountMinor: Long
)

object ShoppingListItem {
  // Client sends quantity, currency_code, unit_amount_minor only.
  // line_amount_minor is computed server-side as quantity * unit_amount_minor.
  implicit val reads: Reads[ShoppingListItem] = (
    (__ \ "quantity").read[Int](Reads.min[Int](1)) and
    (__ \ "currency_code").read[String](Reads.minLength[String](3) keepAnd Reads.maxLength[String](3)) and
    (__ \ "unit_amount_minor").read[Long](Reads.min[Long](0L))
  )((quantity, currencyCode, unitAmountMinor) =>
    ShoppingListItem(quantity, currencyCode, unitAmountMinor, quantity.toLong * unitAmountMinor)
  )

  implicit val writes: Writes[ShoppingListItem] = (
    (__ \ "quantity").write[Int] and
    (__ \ "currency_code").write[String] and
    (__ \ "unit_amount_minor").write[Long] and
    (__ \ "line_amount_minor").write[Long]
  )(sl => (sl.quantity, sl.currencyCode, sl.unitAmountMinor, sl.lineAmountMinor))
}
