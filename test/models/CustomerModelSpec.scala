package models

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.*

class CustomerModelSpec extends AnyWordSpec with Matchers {

  private val testCustomer = Customer(email = "test@example.com", userId = 1L)

  "Customer JSON serialization" should {

    "serialize to JSON with only email (userId excluded)" in {
      val json = Json.toJson(testCustomer)

      (json \ "email").as[String] shouldBe "test@example.com"
      (json \ "userId").toOption shouldBe None
    }
  }
}
