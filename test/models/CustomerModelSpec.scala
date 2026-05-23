package models

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.*
import java.util.UUID

class CustomerModelSpec extends AnyWordSpec with Matchers {

  private val testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
  private val testCustomer = Customer(id = testId, email = "test@example.com")

  "Customer JSON serialization" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(testCustomer)

      (json \ "id").as[String] shouldBe "550e8400-e29b-41d4-a716-446655440000"
      (json \ "email").as[String] shouldBe "test@example.com"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "id" -> "550e8400-e29b-41d4-a716-446655440000",
        "email" -> "test@example.com"
      )

      val result = json.as[Customer]

      result shouldBe testCustomer
    }

    "fail to deserialize when email is missing" in {
      val json = Json.obj("id" -> "550e8400-e29b-41d4-a716-446655440000")

      json.validate[Customer] shouldBe a[JsError]
    }

    "fail to deserialize when id is invalid UUID" in {
      val json = Json.obj("id" -> "not-a-uuid", "email" -> "test@example.com")

      an[IllegalArgumentException] should be thrownBy json.as[Customer]
    }
  }
}
