package models.requests

import models.ShoppingListItem
import play.api.libs.json.{Json, Reads}

case class ShoppingListCreateRequest(
  email:  String,
  name: String,
  items: List[ShoppingListItem]
)

object ShoppingListCreateRequest {
  implicit val reads: Reads[ShoppingListCreateRequest] = Json.reads[ShoppingListCreateRequest]
}
