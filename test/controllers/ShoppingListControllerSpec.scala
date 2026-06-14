package controllers

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import play.api.test.*
import play.api.test.Helpers.*
import play.api.libs.json.*
import services.ShoppingListService
import models.{ShoppingListWithItems, ShoppingListItem}
import helpers.StubAuth

import scala.concurrent.{ExecutionContext, Future}

class ShoppingListControllerSpec extends AnyWordSpec with Matchers {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  private def createFixture() = {
    val mockService = mock(classOf[ShoppingListService])
    val controller = new ShoppingListController(Helpers.stubControllerComponents(), StubAuth.action, mockService)
    (controller, mockService)
  }

  private val testList = ShoppingListWithItems("test@example.com", "Weekly Groceries", List(
    ShoppingListItem("Milk", 2),
    ShoppingListItem("Bread", 1)
  ))

  "getShoppingList" should {

    "return 200 with shopping list JSON when found" in {
      val (controller, mockService) = createFixture()
      when(mockService.getShoppingList("user@example.com")).thenReturn(Future.successful(Right(testList)))

      val result = controller.getShoppingList("user@example.com").apply(FakeRequest())

      status(result) shouldBe OK
      val json = contentAsJson(result)
      (json \ "name").as[String] shouldBe "Weekly Groceries"
      val items = (json \ "items").as[List[JsObject]]
      items.length shouldBe 2
    }

    "return 404 when no shopping list exists" in {
      val (controller, mockService) = createFixture()
      when(mockService.getShoppingList("nobody@example.com"))
        .thenReturn(Future.successful(Left("No shopping list found for email nobody@example.com.")))

      val result = controller.getShoppingList("nobody@example.com").apply(FakeRequest())

      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "error").as[String] should include("No shopping list found")
    }
  }

  "create" should {

    "return 201 with shopping list JSON on success" in {
      val (controller, mockService) = createFixture()
      when(mockService.create(anyString(), anyString(), any())).thenReturn(Future.successful(Right(testList)))

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Weekly Groceries",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 2))
        ))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe CREATED
      (contentAsJson(result) \ "name").as[String] shouldBe "Weekly Groceries"
    }

    "return 409 when shopping list already exists" in {
      val (controller, mockService) = createFixture()
      when(mockService.create(anyString(), anyString(), any()))
        .thenReturn(Future.successful(Left("Shopping list already exists for email user@example.com")))

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Another List",
          "items" -> Json.arr(Json.obj("name" -> "Eggs", "quantity" -> 6))
        ))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe CONFLICT
      (contentAsJson(result) \ "error").as[String] should include("already exists")
    }

    "return 400 when request body is invalid" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("bad" -> "data"))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] shouldBe "Invalid request format"
    }

    "return 400 when name is empty" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 2))
        ))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when items is empty list" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Groceries",
          "items" -> Json.arr()
        ))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when item name is empty" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Groceries",
          "items" -> Json.arr(Json.obj("name" -> "", "quantity" -> 2))
        ))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when item quantity is zero" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Groceries",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 0))
        ))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when item quantity is negative" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Groceries",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> -1))
        ))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when name exceeds 20 characters" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "a" * 21,
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 1))
        ))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when items list exceeds 50 items" in {
      val (controller, _) = createFixture()

      val items = (1 to 51).map(i => Json.obj("name" -> s"Item $i", "quantity" -> 1))
      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Groceries",
          "items" -> Json.toJson(items)
        ))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "getShoppingLists" should {
    "return empty array when no shopping lists found" in {
      val (controller, mockService) = createFixture()
      when(mockService.getShoppingLists(anyString())).thenReturn(Future.successful(Right(List())))

      val result = controller.getShoppingLists("user@example.com").apply(FakeRequest())

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.arr()
    }

    "return 200 with single shopping list" in {
      val (controller, mockService) = createFixture()
      when(mockService.getShoppingLists("user@example.com"))
        .thenReturn(Future.successful(Right(List(testList))))

      val result = controller.getShoppingLists("user@example.com").apply(FakeRequest())

      status(result) shouldBe OK
      val json = contentAsJson(result).as[List[JsObject]]
      json.length shouldBe 1
      (json.head \ "name").as[String] shouldBe "Weekly Groceries"
    }

    "return 200 with multiple shopping lists" in {
      val (controller, mockService) = createFixture()
      val secondList = ShoppingListWithItems("user@example.com", "Hardware", List(ShoppingListItem("Nails", 20)))
      when(mockService.getShoppingLists("user@example.com"))
        .thenReturn(Future.successful(Right(List(testList, secondList))))

      val result = controller.getShoppingLists("user@example.com").apply(FakeRequest())

      status(result) shouldBe OK
      val json = contentAsJson(result).as[List[JsObject]]
      json.length shouldBe 2
    }

    "return 500 when service returns an unexpected error" in {
      val (controller, mockService) = createFixture()
      when(mockService.getShoppingLists("user@example.com"))
        .thenReturn(Future.successful(Left("Database connection failed")))

      val result = controller.getShoppingLists("user@example.com").apply(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "error").as[String] should include("Database connection failed")
    }
  }
}
