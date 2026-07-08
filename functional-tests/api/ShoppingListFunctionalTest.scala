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

    "return 401 when valid token email is not in the allowlist (GET)" in {
      val request = FakeRequest(GET, "/api/v1/customers/test@example.com/shopping-lists")
        .withHeaders(authHeader("intruder@example.com"))
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
      (contentAsJson(response) \ "error").as[String] mustBe "Access denied: intruder@example.com is not authorized"
    }

    "return 401 when valid token email is not in the allowlist (POST)" in {
      val request = FakeRequest(POST, "/api/v1/customers/test@example.com/shopping-lists")
        .withHeaders(authHeader("intruder@example.com"), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Sneaky List",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 1))
        ))
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
      (contentAsJson(response) \ "error").as[String] mustBe "Access denied: intruder@example.com is not authorized"
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

    "return 409 when creating a shopping list with the same name for the same customer" in {
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "dup-shopper@test.com"))
      status(route(app, createCustomer).get) mustBe CREATED

      val createList = FakeRequest(POST, "/api/v1/customers/dup-shopper@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Groceries",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 1))
        ))
      status(route(app, createList).get) mustBe CREATED

      val duplicateList = FakeRequest(POST, "/api/v1/customers/dup-shopper@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Groceries",
          "items" -> Json.arr(Json.obj("name" -> "Bread", "quantity" -> 1))
        ))
      val duplicateResult = route(app, duplicateList).get
      status(duplicateResult) mustBe CONFLICT
      (contentAsJson(duplicateResult) \ "error").as[String] must include("already exists")
    }

    "allow creating two shopping lists with different names for the same customer" in {
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "multi-list@test.com"))
      status(route(app, createCustomer).get) mustBe CREATED

      val firstList = FakeRequest(POST, "/api/v1/customers/multi-list@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Groceries",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 2))
        ))
      status(route(app, firstList).get) mustBe CREATED

      val secondList = FakeRequest(POST, "/api/v1/customers/multi-list@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Hardware",
          "items" -> Json.arr(Json.obj("name" -> "Nails", "quantity" -> 20))
        ))
      status(route(app, secondList).get) mustBe CREATED

      val getLists = route(app, FakeRequest(GET, "/api/v1/customers/multi-list@test.com/shopping-lists")
        .withHeaders(authHeader())).get
      val lists = contentAsJson(getLists).as[List[JsObject]]
      lists.length mustBe 2
    }

    "allow creating a shopping list with the same name for a different customer" in {
      val createAlice = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "alice-unique@test.com"))
      status(route(app, createAlice).get) mustBe CREATED

      val createBob = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "bob-unique@test.com"))
      status(route(app, createBob).get) mustBe CREATED

      val aliceList = FakeRequest(POST, "/api/v1/customers/alice-unique@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Groceries",
          "items" -> Json.arr(Json.obj("name" -> "Milk", "quantity" -> 1))
        ))
      status(route(app, aliceList).get) mustBe CREATED

      val bobList = FakeRequest(POST, "/api/v1/customers/bob-unique@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Groceries",
          "items" -> Json.arr(Json.obj("name" -> "Bread", "quantity" -> 2))
        ))
      status(route(app, bobList).get) mustBe CREATED
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
