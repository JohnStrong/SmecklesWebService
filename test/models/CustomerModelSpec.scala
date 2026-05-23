package models

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.*

class CustomerModelSpec extends AnyWordSpec with Matchers {

  private val testCustomer = Customer(email = "test@example.com")

  "Customer JSON serialization" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(testCustomer)

      (json \ "email").as[String] shouldBe "test@example.com"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj("email" -> "test@example.com")

      json.as[Customer] shouldBe testCustomer
    }

    "fail to deserialize when email is missing" in {
      val json = Json.obj("name" -> "no email")

      json.validate[Customer] shouldBe a[JsError]
    }
  }
}
