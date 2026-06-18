package models

import play.api.libs.json._

case class Customer(email: String, userId: Long)

object Customer {
  // Only serialize email in API responses — userId is internal DB info
  implicit val writes: Writes[Customer] = (c: Customer) => Json.obj("email" -> c.email)
}
