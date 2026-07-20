package models

import play.api.libs.json._

case class Customer(email: String, userId: Long, currencyCode: String)

object Customer {
  // Serialize email and currency_code in API responses — userId is internal DB info
  implicit val writes: Writes[Customer] = (c: Customer) => Json.obj(
    "email" -> c.email,
    "currency_code" -> c.currencyCode
  )
}
