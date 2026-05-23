package models

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.*

class ShoppingListItemModelSpec extends AnyWordSpec with Matchers {

  private val testItem = ShoppingListItem("Milk", 2)

  "ShoppingListItem JSON serialization" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(testItem)

      (json \ "name").as[String] shouldBe "Milk"
      (json \ "quantity").as[Int] shouldBe 2
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj("name" -> "Milk", "quantity" -> 2)

      json.as[ShoppingListItem] shouldBe testItem
    }

    "fail to deserialize when name is missing" in {
      val json = Json.obj("quantity" -> 2)

      json.validate[ShoppingListItem] shouldBe a[JsError]
    }

    "fail to deserialize when quantity is missing" in {
      val json = Json.obj("name" -> "Milk")

      json.validate[ShoppingListItem] shouldBe a[JsError]
    }

    "fail to deserialize when name is empty" in {
      val json = Json.obj("name" -> "", "quantity" -> 2)

      json.validate[ShoppingListItem] shouldBe a[JsError]
    }

    "fail to deserialize when quantity is zero" in {
      val json = Json.obj("name" -> "Milk", "quantity" -> 0)

      json.validate[ShoppingListItem] shouldBe a[JsError]
    }

    "fail to deserialize when quantity is negative" in {
      val json = Json.obj("name" -> "Milk", "quantity" -> -1)

      json.validate[ShoppingListItem] shouldBe a[JsError]
    }
  }
}
