package models

import play.api.libs.json._

case class ShoppingList(name: String, items: List[ShoppingListItem])

object ShoppingList {
  implicit val format: Format[ShoppingList] = Json.format[ShoppingList]
}
