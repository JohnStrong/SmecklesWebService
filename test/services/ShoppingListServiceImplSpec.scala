package services

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import models.{ShoppingList, ShoppingListItem}

class ShoppingListServiceImplSpec extends AnyWordSpec with Matchers {

  private def freshService() = new ShoppingListServiceImpl()

  private val testItems = List(ShoppingListItem("Milk", 2), ShoppingListItem("Bread", 1))

  "getShoppingList" should {

    "return Left with error when no list exists for email" in {
      val service = freshService()
      val result = service.getShoppingList("unknown@example.com")

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("No shopping list found")
    }

    "return Right with shopping list after creation" in {
      val service = freshService()
      service.create("user@example.com", "Groceries", testItems)

      val result = service.getShoppingList("user@example.com")

      result shouldBe Right(ShoppingList("Groceries", testItems))
    }
  }

  "create" should {

    "return Right with new shopping list on success" in {
      val service = freshService()
      val result = service.create("user@example.com", "Groceries", testItems)

      result shouldBe Right(ShoppingList("Groceries", testItems))
    }

    "return Left with error when list already exists for email" in {
      val service = freshService()
      service.create("user@example.com", "Groceries", testItems)

      val result = service.create("user@example.com", "Another", testItems)

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("already exists")
    }
  }
}
