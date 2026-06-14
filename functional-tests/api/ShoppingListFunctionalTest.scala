package api

import org.scalatestplus.play.*
import play.api.libs.json.*
import play.api.test.*
import play.api.test.Helpers.*

class ShoppingListFunctionalTest extends PlaySpec with AuthenticatedFunctionalTest {

  "ShoppingListController" should {

    // --- Auth tests (self-contained for shopping list endpoints) ---

    "return 401 when GET has no Authorization header" in {
      val request = FakeRequest(GET, "/api/v1/customers/test@example.com/shopping-lists")
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
    }

    "return 401 when POST has no Authorization header" in {
      val request = FakeRequest(POST, "/api/v1/customers/test@example.com/shopping-lists")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("name" -> "Test", "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 1))))
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
    }

    "return 401 when token is invalid" in {
      val request = FakeRequest(GET, "/api/v1/customers/test@example.com/shopping-lists")
        .withHeaders("Authorization" -> "Bearer garbage.token")
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
    }

    // --- Behaviour tests ---

    "create a shopping list and retrieve it" in {
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "shopper@test.com"))
      status(route(app, createCustomer).get) mustBe CREATED

      val createList = FakeRequest(POST, "/api/v1/customers/shopper@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
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

      val getResult = route(app, FakeRequest(GET, "/api/v1/customers/shopper@test.com/shopping-lists")
        .withHeaders(authHeader())).get
      status(getResult) mustBe OK
      val lists = contentAsJson(getResult).as[List[JsObject]]
      lists.length mustBe 1
      (lists.head \ "name").as[String] mustBe "Weekly Groceries"
    }

    "return 409 when creating a duplicate shopping list" in {
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "dup-shopper@test.com"))
      status(route(app, createCustomer).get) mustBe CREATED

      val createList = FakeRequest(POST, "/api/v1/customers/dup-shopper@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "First List",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 1))
        ))
      status(route(app, createList).get) mustBe CREATED

      val duplicateList = FakeRequest(POST, "/api/v1/customers/dup-shopper@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Second List",
          "items" -> Json.arr(Json.obj("name" -> "Bread", "quantity" -> 1))
        ))
      val duplicateResult = route(app, duplicateList).get
      status(duplicateResult) mustBe CONFLICT
      (contentAsJson(duplicateResult) \ "error").as[String] must include("already exists")
    }

    "return empty list when no shopping lists exist for email" in {
      val getResult = route(app, FakeRequest(GET, "/api/v1/customers/nobody@test.com/shopping-lists")
        .withHeaders(authHeader())).get
      status(getResult) mustBe OK
      contentAsJson(getResult).as[List[JsObject]] mustBe empty
    }

    "return 400 when name is empty" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 1))
        ))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when items list is empty" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Test",
          "items" -> Json.arr()
        ))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when item name is empty" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Test",
          "items" -> Json.arr(Json.obj("name" -> "", "quantity" -> 1))
        ))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when item quantity is less than 1" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Test",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 0))
        ))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when request body is missing required fields" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("bad" -> "data"))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] mustBe "Invalid request format"
    }

    "return 400 when name exceeds 20 characters" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "a" * 21,
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 1))
        ))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when items list exceeds 50 items" in {
      val items = (1 to 51).map(i => Json.obj("name" -> s"Item $i", "quantity" -> 1))
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Test",
          "items" -> Json.toJson(items)
        ))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
    }
  }
}
