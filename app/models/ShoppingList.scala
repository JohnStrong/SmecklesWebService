package models

import play.api.libs.json._
import play.api.libs.functional.syntax._
import java.time.LocalDate

// Internal Service Domain Model (service -> repo interfaces use this)
case class ShoppingListWithItems(
  email: String,
  name: String,
  periodStart: LocalDate,
  dayDate: LocalDate,
  items: List[ShoppingListItem]
)
object ShoppingListWithItems {
  implicit val reads: Reads[ShoppingListWithItems] = (
    (__ \ "email").read[String] and
      (__ \ "name").read[String] and
      (__ \ "period_start").read[LocalDate] and
      (__ \ "day_date").read[LocalDate] and
      (__ \ "items").read[List[ShoppingListItem]]
    )(ShoppingListWithItems.apply _)

  implicit val writes: Writes[ShoppingListWithItems] = (
    (__ \ "email").write[String] and
      (__ \ "name").write[String] and
      (__ \ "period_start").write[LocalDate] and
      (__ \ "day_date").write[LocalDate] and
      (__ \ "items").write[List[ShoppingListItem]]
    )(sl => (sl.email, sl.name, sl.periodStart, sl.dayDate, sl.items))
}

@Deprecated("Use DecoupledShoppingList instead")
case class ShoppingList(email: String, name: String, items: List[ShoppingListItem])
@Deprecated
object ShoppingList {
  implicit val format: Format[ShoppingList] = Json.format[ShoppingList]
}