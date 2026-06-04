package services

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import models.{ShoppingListWithItems, ShoppingListItem}
import org.scalatest.concurrent.ScalaFutures
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import repositories.DataRepository

import scala.concurrent.Future

class ShoppingListServiceImplSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private val testItems = List(ShoppingListItem("Milk", 2), ShoppingListItem("Bread", 1))
  private val testList = ShoppingListWithItems("user@example.com", "Groceries", testItems)

  private def freshService() = {
    val mockRepo = mock(classOf[DataRepository[String, ShoppingListWithItems]])
    (new ShoppingListServiceImpl(mockRepo), mockRepo)
  }

  "getShoppingList" should {

    "return Left with error when no list exists for email" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.findByIdentifier("unknown@example.com"))
        .thenReturn(Future.successful(Left("No shopping list found for email unknown@example.com.")))

      val result = service.getShoppingList("unknown@example.com").futureValue

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("No shopping list found")
    }

    "return Right with shopping list after creation" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.findByIdentifier("user@example.com"))
        .thenReturn(Future.successful(Right(testList)))

      val result = service.getShoppingList("user@example.com").futureValue

      result shouldBe Right(testList)
    }
  }

  "create" should {

    "return Right with new shopping list on success" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.create(any[ShoppingListWithItems]()))
        .thenReturn(Future.successful(Right(testList)))

      val result = service.create("user@example.com", "Groceries", testItems).futureValue

      result shouldBe Right(testList)
    }

    "return Left with error when list already exists for email" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.create(any[ShoppingListWithItems]()))
        .thenReturn(Future.successful(Left("Shopping list already exists for email user@example.com.")))

      val result = service.create("user@example.com", "Groceries", testItems).futureValue

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("already exists")
    }
  }
}
