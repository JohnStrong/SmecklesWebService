package controllers

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import play.api.test.*
import play.api.test.Helpers.*
import play.api.libs.json.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import services.ShoppingListService
import models.{ShoppingList, ShoppingListItem}

class ShoppingListControllerSpec extends AnyWordSpec with Matchers {

  implicit private val system: ActorSystem = ActorSystem("test")
  implicit private val mat: Materializer = Materializer.matFromSystem

  private def createFixture() = {
    val mockService = mock(classOf[ShoppingListService])
    val stubComponents = Helpers.stubControllerComponents()
    val controller = new ShoppingListController(stubComponents, mockService)
    (controller, mockService)
  }

  private val testList = ShoppingList("Weekly Groceries", List(
    ShoppingListItem("Milk", 2),
    ShoppingListItem("Bread", 1)
  ))

  "getShoppingList" should {

    "return 200 with shopping list JSON when found" in {
      val (controller, mockService) = createFixture()
      when(mockService.getShoppingList("user@example.com")).thenReturn(Right(testList))

      val result = controller.getShoppingList("user@example.com").apply(FakeRequest())

      status(result) shouldBe OK
      val json = contentAsJson(result)
      (json \ "name").as[String] shouldBe "Weekly Groceries"
      val items = (json \ "items").as[List[JsObject]]
      items.length shouldBe 2
      (items.head \ "name").as[String] shouldBe "Milk"
      (items.head \ "quantity").as[Int] shouldBe 2
      (items(1) \ "name").as[String] shouldBe "Bread"
      (items(1) \ "quantity").as[Int] shouldBe 1
    }

    "return 404 when no shopping list exists" in {
      val (controller, mockService) = createFixture()
      when(mockService.getShoppingList("nobody@example.com"))
        .thenReturn(Left("No shopping list found for email nobody@example.com."))

      val result = controller.getShoppingList("nobody@example.com").apply(FakeRequest())

      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "error").as[String] should include("No shopping list found")
    }
  }

  "create" should {

    "return 201 with shopping list JSON on success" in {
      val (controller, mockService) = createFixture()
      when(mockService.create(anyString(), anyString(), any())).thenReturn(Right(testList))

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "email" -> "user@example.com",
          "name" -> "Weekly Groceries",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 2))
        ))
      val result = call(controller.create(), request)

      status(result) shouldBe CREATED
      (contentAsJson(result) \ "name").as[String] shouldBe "Weekly Groceries"
    }

    "return 409 when shopping list already exists" in {
      val (controller, mockService) = createFixture()
      when(mockService.create(anyString(), anyString(), any()))
        .thenReturn(Left("Shopping list already exists for email user@example.com"))

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "email" -> "user@example.com",
          "name" -> "Another List",
          "items" -> Json.arr(Json.obj("name" -> "Eggs", "quantity" -> 6))
        ))
      val result = call(controller.create(), request)

      status(result) shouldBe CONFLICT
      (contentAsJson(result) \ "error").as[String] should include("already exists")
    }

    "return 400 when request body is invalid" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("bad" -> "data"))
      val result = call(controller.create(), request)

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] shouldBe "Invalid request format"
    }

    "return 400 when email is empty" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "email" -> "",
          "name" -> "Groceries",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 2))
        ))
      val result = call(controller.create(), request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when name is empty" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "email" -> "user@example.com",
          "name" -> "",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 2))
        ))
      val result = call(controller.create(), request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when items is empty list" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "email" -> "user@example.com",
          "name" -> "Groceries",
          "items" -> Json.arr()
        ))
      val result = call(controller.create(), request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when item name is empty" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "email" -> "user@example.com",
          "name" -> "Groceries",
          "items" -> Json.arr(Json.obj("name" -> "", "quantity" -> 2))
        ))
      val result = call(controller.create(), request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when item quantity is zero" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "email" -> "user@example.com",
          "name" -> "Groceries",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 0))
        ))
      val result = call(controller.create(), request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when item quantity is negative" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "email" -> "user@example.com",
          "name" -> "Groceries",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> -1))
        ))
      val result = call(controller.create(), request)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
