package api

import org.scalatestplus.play.*
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.*
import play.api.test.*
import play.api.test.Helpers.*

class ShoppingListFunctionalTest extends PlaySpec with GuiceOneAppPerSuite {

  "ShoppingListController" should {

    "create a shopping list and retrieve it" in {
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "functional@test.com"))
      status(route(app, createCustomer).get) mustBe CREATED

      val createList = FakeRequest(POST, "/api/v1/customers/functional@test.com/shopping-lists")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Weekly Groceries",
          "items" -> Json.arr(
            Json.obj("name" -> "Milk", "quantity" -> 2),
            Json.obj("name" -> "Bread", "quantity" -> 1)
          )
        ))
      val createResult = route(app, createList).get
      status(createResult) mustBe CREATED

      val json = contentAsJson(createResult)
      (json \ "name").as[String] mustBe "Weekly Groceries"
      (json \ "items").as[List[JsObject]].length mustBe 2

      val getResult = route(app, FakeRequest(GET, "/api/v1/customers/functional@test.com/shopping-lists")).get
      status(getResult) mustBe OK
      (contentAsJson(getResult) \ "name").as[String] mustBe "Weekly Groceries"
    }

    "return 409 when creating a duplicate shopping list" in {
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "duplicate@test.com"))
      status(route(app, createCustomer).get) mustBe CREATED

      val createList = FakeRequest(POST, "/api/v1/customers/duplicate@test.com/shopping-lists")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "First List",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 1))
        ))
      status(route(app, createList).get) mustBe CREATED

      val duplicateList = FakeRequest(POST, "/api/v1/customers/duplicate@test.com/shopping-lists")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Second List",
          "items" -> Json.arr(Json.obj("name" -> "Bread", "quantity" -> 1))
        ))
      val duplicateResult = route(app, duplicateList).get
      status(duplicateResult) mustBe CONFLICT
      (contentAsJson(duplicateResult) \ "error").as[String] must include("already exists")
    }

    "return 404 when getting a shopping list that does not exist" in {
      val getResult = route(app, FakeRequest(GET, "/api/v1/customers/nobody@test.com/shopping-lists")).get
      status(getResult) mustBe NOT_FOUND
      (contentAsJson(getResult) \ "error").as[String] must include("No shopping list found")
    }

    "return 400 when name is empty" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 1))
        ))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when items list is empty" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Test",
          "items" -> Json.arr()
        ))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when item name is empty" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Test",
          "items" -> Json.arr(Json.obj("name" -> "", "quantity" -> 1))
        ))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when item quantity is less than 1" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Test",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 0))
        ))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when request body is missing required fields" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("bad" -> "data"))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] mustBe "Invalid request format"
    }
  }
}
