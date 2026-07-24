package models

import play.api.libs.json.*
import play.api.libs.functional.syntax._

case class ShoppingListItem(
  name: String,
  quantity: Int,
  unitAmountMinor: Long,
  lineAmountMinor: Long,
  status: String = "pending"
)

object ShoppingListItem {
  // Client sends name, quantity, unit_amount_minor.
  // line_amount_minor is computed server-side as quantity * unit_amount_minor.
  // status defaults to "pending" on creation.
  implicit val reads: Reads[ShoppingListItem] = (
    (__ \ "name").read[String](Reads.minLength[String](1)) and
    (__ \ "quantity").read[Int](Reads.min[Int](1)) and
    (__ \ "unit_amount_minor").read[Long](Reads.min[Long](0L))
  )((name, quantity, unitAmountMinor) =>
    ShoppingListItem(name, quantity, unitAmountMinor, quantity.toLong * unitAmountMinor)
  )

  implicit val writes: Writes[ShoppingListItem] = (
    (__ \ "name").write[String] and
    (__ \ "quantity").write[Int] and
    (__ \ "unit_amount_minor").write[Long] and
    (__ \ "line_amount_minor").write[Long] and
    (__ \ "status").write[String]
  )(sl => (sl.name, sl.quantity, sl.unitAmountMinor, sl.lineAmountMinor, sl.status))
}
