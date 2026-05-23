package models

import play.api.libs.json._

case class Customer(email: String)

object Customer {
  implicit val format: Format[Customer] = Json.format[Customer]
}
