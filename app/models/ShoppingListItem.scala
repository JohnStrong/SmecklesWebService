package models

import play.api.libs.json.*
import play.api.libs.functional.syntax._

@Deprecated("Use DecoupledShoppingListItem instead")
case class ShoppingListItem(name: String, quantity: Int)

// TODO: decouple items with shopping list , you can have 'Milk' in many shopping lists for instance
// TODO: See. https://github.com/JohnStrong/ShoppingListWebService/issues/2
case class DecoupledShoppingListItem(
  id: Long, // item id
  shoppingListId: Long, // id of the shopping list this item belongs to
  name: String, // the item name
  quantity: Int // amount of the item
)

object ShoppingListItem {
  implicit val reads: Reads[ShoppingListItem] = (
    (__ \ "name").read[String](Reads.minLength[String](1)) and
    (__ \ "quantity").read[Int](Reads.min[Int](1))
  )(ShoppingListItem.apply _)

  implicit val writes: Writes[ShoppingListItem] = Json.writes[ShoppingListItem]
}
