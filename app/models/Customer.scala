package models

import play.api.libs.json._
import java.util.UUID

case class Customer(id: UUID, email: String)

object Customer {
  implicit val uuidFormat: Format[UUID] = Format(
    Reads(js => js.validate[String].map(UUID.fromString)),
    Writes(uuid => JsString(uuid.toString))
  )
  implicit val format: Format[Customer] = Json.format[Customer]
}
