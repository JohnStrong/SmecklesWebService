package models.requests

import models.ShoppingListItem
import play.api.libs.json.{Json, Reads, __}
import play.api.libs.functional.syntax._

case class ShoppingListCreateRequest(
  email:  String,
  name: String,
  items: List[ShoppingListItem]
)

object ShoppingListCreateRequest {
  implicit val reads: Reads[ShoppingListCreateRequest] = (
    (__ \ "email").read[String](Reads.minLength[String](1)) and
    (__ \ "name").read[String](Reads.minLength[String](1)) and
    (__ \ "items").read[List[ShoppingListItem]](Reads.minLength[List[ShoppingListItem]](1))
  )(ShoppingListCreateRequest.apply _)
}
