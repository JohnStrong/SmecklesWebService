package models

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.*

class ShoppingListItemModelSpec extends AnyWordSpec with Matchers {

  // line_amount_minor = quantity * unit_amount_minor = 2 * 129 = 258
  private val testItem = ShoppingListItem(name = "Milk", quantity = 2, unitAmountMinor = 129L, lineAmountMinor = 258L)

  "ShoppingListItem JSON serialization" should {

    "serialize to JSON correctly (includes computed line_amount_minor)" in {
      val json = Json.toJson(testItem)

      (json \ "name").as[String] shouldBe "Milk"
      (json \ "quantity").as[Int] shouldBe 2
      (json \ "unit_amount_minor").as[Long] shouldBe 129L
      (json \ "line_amount_minor").as[Long] shouldBe 258L
    }

    "deserialize from JSON correctly and compute line_amount_minor" in {
      val json = Json.obj("name" -> "Milk", "quantity" -> 2, "unit_amount_minor" -> 129)

      json.as[ShoppingListItem] shouldBe testItem
    }

    "compute line_amount_minor as quantity * unit_amount_minor" in {
      val json = Json.obj("name" -> "Bananas", "quantity" -> 3, "unit_amount_minor" -> 500)

      val item = json.as[ShoppingListItem]
      item.lineAmountMinor shouldBe 1500L
    }

    "fail to deserialize when name is missing" in {
      val json = Json.obj("quantity" -> 2, "unit_amount_minor" -> 129)

      json.validate[ShoppingListItem] shouldBe a[JsError]
    }

    "fail to deserialize when name is empty" in {
      val json = Json.obj("name" -> "", "quantity" -> 2, "unit_amount_minor" -> 129)

      json.validate[ShoppingListItem] shouldBe a[JsError]
    }

    "fail to deserialize when quantity is missing" in {
      val json = Json.obj("name" -> "Milk", "unit_amount_minor" -> 129)

      json.validate[ShoppingListItem] shouldBe a[JsError]
    }

    "fail to deserialize when unit_amount_minor is missing" in {
      val json = Json.obj("name" -> "Milk", "quantity" -> 2)

      json.validate[ShoppingListItem] shouldBe a[JsError]
    }

    "fail to deserialize when quantity is zero" in {
      val json = Json.obj("name" -> "Milk", "quantity" -> 0, "unit_amount_minor" -> 129)

      json.validate[ShoppingListItem] shouldBe a[JsError]
    }

    "fail to deserialize when quantity is negative" in {
      val json = Json.obj("name" -> "Milk", "quantity" -> -1, "unit_amount_minor" -> 129)

      json.validate[ShoppingListItem] shouldBe a[JsError]
    }

    "fail to deserialize when unit_amount_minor is negative" in {
      val json = Json.obj("name" -> "Milk", "quantity" -> 2, "unit_amount_minor" -> -1)

      json.validate[ShoppingListItem] shouldBe a[JsError]
    }

    "accept zero for unit_amount_minor (line_amount_minor becomes 0)" in {
      val json = Json.obj("name" -> "Free Sample", "quantity" -> 1, "unit_amount_minor" -> 0)

      val result = json.validate[ShoppingListItem]
      result shouldBe a[JsSuccess[_]]
      result.get.lineAmountMinor shouldBe 0L
    }
  }
}
