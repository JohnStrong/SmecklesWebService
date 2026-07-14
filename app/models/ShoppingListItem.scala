package models

import play.api.libs.json.*
import play.api.libs.functional.syntax._

case class ShoppingListItem(
  name: String,
  quantity: Int,
  currencyCode: String,
  unitAmountMinor: Long,
  lineAmountMinor: Long,
  status: String = "pending"
)

object ShoppingListItem {
  // Client sends name, quantity, currency_code, unit_amount_minor.
  // line_amount_minor is computed server-side as quantity * unit_amount_minor.
  // status defaults to "pending" on creation.
  implicit val reads: Reads[ShoppingListItem] = (
    (__ \ "name").read[String](Reads.minLength[String](1)) and
    (__ \ "quantity").read[Int](Reads.min[Int](1)) and
    (__ \ "currency_code").read[String](Reads.minLength[String](3) keepAnd Reads.maxLength[String](3)) and
    (__ \ "unit_amount_minor").read[Long](Reads.min[Long](0L))
  )((name, quantity, currencyCode, unitAmountMinor) =>
    ShoppingListItem(name, quantity, currencyCode, unitAmountMinor, quantity.toLong * unitAmountMinor)
  )

  implicit val writes: Writes[ShoppingListItem] = (
    (__ \ "name").write[String] and
    (__ \ "quantity").write[Int] and
    (__ \ "currency_code").write[String] and
    (__ \ "unit_amount_minor").write[Long] and
    (__ \ "line_amount_minor").write[Long] and
    (__ \ "status").write[String]
  )(sl => (sl.name, sl.quantity, sl.currencyCode, sl.unitAmountMinor, sl.lineAmountMinor, sl.status))
}
