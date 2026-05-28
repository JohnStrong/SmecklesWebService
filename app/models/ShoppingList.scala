package models

import play.api.libs.json._

// TODO - refactor to decouple items to list using an id instead (see <<SlickShoppingListRepository>> )
@Deprecated("Use DecoupledShoppingList instead")
case class ShoppingList(email: String, name: String, items: List[ShoppingListItem])

// Use in favor of <<ShoppingList>>
case class DecoupledShoppingList(id: Long, email: String, name: String)

object ShoppingList {
  implicit val format: Format[ShoppingList] = Json.format[ShoppingList]
}
