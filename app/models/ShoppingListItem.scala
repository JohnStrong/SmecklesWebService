package models

import play.api.libs.json.*
import play.api.libs.functional.syntax._

// TODO: move this into its own class inside mo
case class ShoppingListItem(name: String, quantity: Int)

object ShoppingListItem {
  implicit val reads: Reads[ShoppingListItem] = (
    (__ \ "name").read[String](Reads.minLength[String](1)) and
    (__ \ "quantity").read[Int](Reads.min[Int](1))
  )(ShoppingListItem.apply _)

  implicit val writes: Writes[ShoppingListItem] = Json.writes[ShoppingListItem]
}
