package models.requests

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsSuccess, Json}

class CustomerCreateRequestSpec extends AnyWordSpec with Matchers {

  "CustomerCreateRequest" should {

    "deserialize successfully with valid fields" in {
      val json = Json.obj("email" -> "user@example.com", "currency_code" -> "GBP")
      val result = json.validate[CustomerCreateRequest]

      result shouldBe a[JsSuccess[_]]
      result.get.email shouldBe "user@example.com"
      result.get.currencyCode shouldBe "GBP"
    }

    "accept any 3-character currency code" in {
      val json = Json.obj("email" -> "user@example.com", "currency_code" -> "USD")
      val result = json.validate[CustomerCreateRequest]

      result shouldBe a[JsSuccess[_]]
      result.get.currencyCode shouldBe "USD"
    }

    "fail when email is missing" in {
      val json = Json.obj("currency_code" -> "GBP")
      val result = json.validate[CustomerCreateRequest]

      result.isError shouldBe true
    }

    "fail when email is empty string" in {
      val json = Json.obj("email" -> "", "currency_code" -> "GBP")
      val result = json.validate[CustomerCreateRequest]

      result.isError shouldBe true
    }

    "fail when currency_code is missing" in {
      val json = Json.obj("email" -> "user@example.com")
      val result = json.validate[CustomerCreateRequest]

      result.isError shouldBe true
    }

    "fail when currency_code is too short" in {
      val json = Json.obj("email" -> "user@example.com", "currency_code" -> "GB")
      val result = json.validate[CustomerCreateRequest]

      result.isError shouldBe true
    }

    "fail when currency_code is too long" in {
      val json = Json.obj("email" -> "user@example.com", "currency_code" -> "GBPP")
      val result = json.validate[CustomerCreateRequest]

      result.isError shouldBe true
    }

    "fail when currency_code is empty string" in {
      val json = Json.obj("email" -> "user@example.com", "currency_code" -> "")
      val result = json.validate[CustomerCreateRequest]

      result.isError shouldBe true
    }
  }
}
