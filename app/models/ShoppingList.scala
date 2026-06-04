package models

import play.api.libs.json._

// Internal Service Domain Model (service -> repo interfaces use this)
case class ShoppingListWithItems(
  email: String,
  name: String,
  items: List[ShoppingListItem]
)

@Deprecated("Use DecoupledShoppingList instead")
case class ShoppingList(email: String, name: String, items: List[ShoppingListItem])
@Deprecated
object ShoppingList {
  implicit val format: Format[ShoppingList] = Json.format[ShoppingList]
}