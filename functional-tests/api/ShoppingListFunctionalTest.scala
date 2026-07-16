package api

import org.scalatestplus.play.*
import play.api.libs.json.*
import play.api.test.*
import play.api.test.Helpers.*

class ShoppingListFunctionalTest extends PlaySpec with AuthenticatedFunctionalTest {

  // Helper to create a valid item JSON
  private def validItemJson(name: String = "Milk", quantity: Int = 1, currencyCode: String = "GBP", unitAmountMinor: Long = 100L) =
    Json.obj("name" -> name, "quantity" -> quantity, "currency_code" -> currencyCode, "unit_amount_minor" -> unitAmountMinor)

  // Helper to create a valid create request body
  private def validCreateBody(
    name: String = "Groceries",
    periodStart: String = "2026-07-01",
    dayDate: String = "2026-07-05",
    items: JsArray = Json.arr(validItemJson())
  ) = Json.obj("name" -> name, "period_start" -> periodStart, "day_date" -> dayDate, "items" -> items)

  "ShoppingListController" should {

    // --- Auth tests ---

    "return 401 when GET has no Authorization header" in {
      val request = FakeRequest(GET, "/api/v1/customers/test@example.com/shopping-lists")
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
    }

    "return 401 when POST has no Authorization header" in {
      val request = FakeRequest(POST, "/api/v1/customers/test@example.com/shopping-lists")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(validCreateBody())
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
        .withBody(validCreateBody(name = "Sneaky List"))
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
      (contentAsJson(response) \ "error").as[String] mustBe "Access denied: intruder@example.com is not authorized"
    }

    // --- Behaviour tests ---

    "create a shopping list and retrieve it then delete it" in {
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "shopper@test.com"))
      status(route(app, createCustomer).get) mustBe CREATED

      val createList = FakeRequest(POST, "/api/v1/customers/shopper@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(
          name = "Weekly Groceries",
          periodStart = "2026-07-01",
          dayDate = "2026-07-05",
          items = Json.arr(
            validItemJson(name = "Milk", quantity = 2, unitAmountMinor = 129L),
            validItemJson(name = "Bread", quantity = 1, unitAmountMinor = 100L)
          )
        ))
      val createResult = route(app, createList).get
      status(createResult) mustBe CREATED

      val json = contentAsJson(createResult)
      (json \ "name").as[String] mustBe "Weekly Groceries"
      (json \ "period_start").as[String] mustBe "2026-07-01"
      (json \ "day_date").as[String] mustBe "2026-07-05"
      (json \ "items").as[List[JsObject]].length mustBe 2

      // Verify line_amount_minor is computed correctly
      val items = (json \ "items").as[List[JsObject]]
      (items.head \ "line_amount_minor").as[Long] mustBe 258L
      (items(1) \ "line_amount_minor").as[Long] mustBe 100L

      // Get all shopping lists and verify period_start/day_date round-trip
      val getResult = route(app, FakeRequest(GET, "/api/v1/customers/shopper@test.com/shopping-lists")
        .withHeaders(authHeader())).get
      status(getResult) mustBe OK
      val lists = contentAsJson(getResult).as[List[JsObject]]
      lists.length mustBe 1
      (lists.head \ "period_start").as[String] mustBe "2026-07-01"
      (lists.head \ "day_date").as[String] mustBe "2026-07-05"
      (lists.head \ "items").as[List[JsObject]].head.apply("line_amount_minor").as[Long] mustBe 258L

      // Delete
      val deleteResult = route(app, FakeRequest(DELETE, "/api/v1/customers/shopper@test.com/shopping-lists/Weekly%20Groceries")
        .withHeaders(authHeader())).get
      status(deleteResult) mustBe NO_CONTENT

      // Confirm gone
      val getResult2 = route(app, FakeRequest(GET, "/api/v1/customers/shopper@test.com/shopping-lists")
        .withHeaders(authHeader())).get
      status(getResult2) mustBe OK
      contentAsJson(getResult2).as[List[JsObject]] mustBe empty
    }

    "return 409 when creating a shopping list with the same name on the same day" in {
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "dup-shopper@test.com"))
      status(route(app, createCustomer).get) mustBe CREATED

      val body = validCreateBody(name = "Groceries", dayDate = "2026-07-05")

      status(route(app, FakeRequest(POST, "/api/v1/customers/dup-shopper@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(body)).get) mustBe CREATED

      val duplicateResult = route(app, FakeRequest(POST, "/api/v1/customers/dup-shopper@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(body)).get
      status(duplicateResult) mustBe CONFLICT
      (contentAsJson(duplicateResult) \ "error").as[String] must include("already exists")
    }

    "allow creating the same list name on different days" in {
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "weekly@test.com"))
      status(route(app, createCustomer).get) mustBe CREATED

      val week1 = validCreateBody(name = "Weekly Groceries", dayDate = "2026-07-05")
      val week2 = validCreateBody(name = "Weekly Groceries", dayDate = "2026-07-12")

      status(route(app, FakeRequest(POST, "/api/v1/customers/weekly@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(week1)).get) mustBe CREATED

      status(route(app, FakeRequest(POST, "/api/v1/customers/weekly@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(week2)).get) mustBe CREATED

      val getLists = route(app, FakeRequest(GET, "/api/v1/customers/weekly@test.com/shopping-lists")
        .withHeaders(authHeader())).get
      contentAsJson(getLists).as[List[JsObject]].length mustBe 2
    }

    "delete only one list when same name exists on different days" in {
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "delete-day@test.com"))
      status(route(app, createCustomer).get) mustBe CREATED

      // Create same list name on two different days
      val july5 = validCreateBody(name = "Weekly Groceries", dayDate = "2026-07-05")
      val july12 = validCreateBody(name = "Weekly Groceries", dayDate = "2026-07-12")

      status(route(app, FakeRequest(POST, "/api/v1/customers/delete-day@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(july5)).get) mustBe CREATED

      status(route(app, FakeRequest(POST, "/api/v1/customers/delete-day@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(july12)).get) mustBe CREATED

      // Delete the July 5 list only (dayDate in path)
      val deleteResult = route(app, FakeRequest(DELETE, "/api/v1/customers/delete-day@test.com/shopping-lists/2026-07-05/Weekly%20Groceries")
        .withHeaders(authHeader())).get
      status(deleteResult) mustBe NO_CONTENT

      // July 12 list should still exist
      val remaining = route(app, FakeRequest(GET, "/api/v1/customers/delete-day@test.com/shopping-lists")
        .withHeaders(authHeader())).get
      val lists = contentAsJson(remaining).as[List[JsObject]]
      lists.length mustBe 1
      (lists.head \ "day_date").as[String] mustBe "2026-07-12"
    }

    "allow creating two shopping lists with different names on the same day" in {
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "multi-list@test.com"))
      status(route(app, createCustomer).get) mustBe CREATED

      val list1 = validCreateBody(name = "Groceries", dayDate = "2026-07-05")
      val list2 = validCreateBody(name = "Hardware", dayDate = "2026-07-05")

      status(route(app, FakeRequest(POST, "/api/v1/customers/multi-list@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(list1)).get) mustBe CREATED

      status(route(app, FakeRequest(POST, "/api/v1/customers/multi-list@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(list2)).get) mustBe CREATED

      val getLists = route(app, FakeRequest(GET, "/api/v1/customers/multi-list@test.com/shopping-lists")
        .withHeaders(authHeader())).get
      contentAsJson(getLists).as[List[JsObject]].length mustBe 2
    }

    "return empty list when no shopping lists exist for email" in {
      val getResult = route(app, FakeRequest(GET, "/api/v1/customers/nobody@test.com/shopping-lists")
        .withHeaders(authHeader())).get
      status(getResult) mustBe OK
      contentAsJson(getResult).as[List[JsObject]] mustBe empty
    }

    // --- Validation tests ---

    "return 400 when name is empty" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(name = ""))
      status(route(app, request).get) mustBe BAD_REQUEST
    }

    "return 400 when items list is empty" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(items = Json.arr()))
      status(route(app, request).get) mustBe BAD_REQUEST
    }

    "return 400 when item quantity is less than 1" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(items = Json.arr(validItemJson(quantity = 0))))
      status(route(app, request).get) mustBe BAD_REQUEST
    }

    "return 400 when currency_code is missing" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(items = Json.arr(Json.obj("quantity" -> 1, "unit_amount_minor" -> 100))))
      status(route(app, request).get) mustBe BAD_REQUEST
    }

    "return 400 when unit_amount_minor is missing" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(items = Json.arr(Json.obj("quantity" -> 1, "currency_code" -> "GBP"))))
      status(route(app, request).get) mustBe BAD_REQUEST
    }

    "return 400 when request body is missing required fields" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("bad" -> "data"))
      val result = route(app, request).get
      status(result) mustBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] mustBe "Invalid request format"
    }

    "return 400 when period_start is not the 1st of the month" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(periodStart = "2026-07-15"))
      status(route(app, request).get) mustBe BAD_REQUEST
    }

    "return 400 when day_date is in a different month than period_start" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(periodStart = "2026-07-01", dayDate = "2026-08-05"))
      status(route(app, request).get) mustBe BAD_REQUEST
    }

    "return 400 when name exceeds 20 characters" in {
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(name = "a" * 21))
      status(route(app, request).get) mustBe BAD_REQUEST
    }

    "return 400 when items list exceeds 50 items" in {
      val items = Json.toJson((1 to 51).map(_ => validItemJson())).as[JsArray]
      val request = FakeRequest(POST, "/api/v1/customers/valid@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(items = items))
      status(route(app, request).get) mustBe BAD_REQUEST
    }

    // --- Update Item Status (PATCH) tests ---

    "mark an item as completed and return 200 with updated item" in {
      // Setup: create customer + shopping list
      status(route(app, FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "patcher@test.com"))).get) mustBe CREATED

      status(route(app, FakeRequest(POST, "/api/v1/customers/patcher@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(name = "Groceries", items = Json.arr(
          validItemJson(name = "Milk", quantity = 2, unitAmountMinor = 129L)
        )))).get) mustBe CREATED

      // Verify item starts as pending
      val getBefore = route(app, FakeRequest(GET, "/api/v1/customers/patcher@test.com/shopping-lists")
        .withHeaders(authHeader())).get
      status(getBefore) mustBe OK
      val itemsBefore = (contentAsJson(getBefore).as[List[JsObject]].head \ "items").as[List[JsObject]]
      (itemsBefore.head \ "name").as[String] mustBe "Milk"
      (itemsBefore.head \ "status").as[String] mustBe "pending"

      // PATCH: mark Milk as completed
      val patchResult = route(app, FakeRequest(PATCH, "/api/v1/customers/patcher@test.com/shopping-lists/Groceries/items/Milk")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("status" -> "completed"))).get
      status(patchResult) mustBe OK

      val json = contentAsJson(patchResult)
      (json \ "name").as[String] mustBe "Milk"
      (json \ "status").as[String] mustBe "completed"
      (json \ "line_amount_minor").as[Long] mustBe 258L

      // Verify the status persists via GET
      val getAfter = route(app, FakeRequest(GET, "/api/v1/customers/patcher@test.com/shopping-lists")
        .withHeaders(authHeader())).get
      status(getAfter) mustBe OK
      val itemsAfter = (contentAsJson(getAfter).as[List[JsObject]].head \ "items").as[List[JsObject]]
      (itemsAfter.head \ "name").as[String] mustBe "Milk"
      (itemsAfter.head \ "status").as[String] mustBe "completed"
    }

    "revert an item back to pending and return 200" in {
      status(route(app, FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "reverter@test.com"))).get) mustBe CREATED

      status(route(app, FakeRequest(POST, "/api/v1/customers/reverter@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(name = "Groceries", items = Json.arr(
          validItemJson(name = "Bread", quantity = 1, unitAmountMinor = 85L)
        )))).get) mustBe CREATED

      // Complete then revert
      status(route(app, FakeRequest(PATCH, "/api/v1/customers/reverter@test.com/shopping-lists/Groceries/items/Bread")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("status" -> "completed"))).get) mustBe OK

      val revertResult = route(app, FakeRequest(PATCH, "/api/v1/customers/reverter@test.com/shopping-lists/Groceries/items/Bread")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("status" -> "pending"))).get
      status(revertResult) mustBe OK
      (contentAsJson(revertResult) \ "status").as[String] mustBe "pending"
    }

    "return 200 when item already has the requested status (idempotent)" in {
      status(route(app, FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "conflict@test.com"))).get) mustBe CREATED

      status(route(app, FakeRequest(POST, "/api/v1/customers/conflict@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(name = "Groceries", items = Json.arr(
          validItemJson(name = "Eggs")
        )))).get) mustBe CREATED

      // Item starts as pending, set pending again — should be a no-op 200
      val result = route(app, FakeRequest(PATCH, "/api/v1/customers/conflict@test.com/shopping-lists/Groceries/items/Eggs")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("status" -> "pending"))).get
      status(result) mustBe OK
      (contentAsJson(result) \ "status").as[String] mustBe "pending"
    }

    "return 404 when item name does not exist" in {
      status(route(app, FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "notfound@test.com"))).get) mustBe CREATED

      status(route(app, FakeRequest(POST, "/api/v1/customers/notfound@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validCreateBody(name = "Groceries", items = Json.arr(
          validItemJson(name = "Milk")
        )))).get) mustBe CREATED

      val result = route(app, FakeRequest(PATCH, "/api/v1/customers/notfound@test.com/shopping-lists/Groceries/items/Nonexistent")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("status" -> "completed"))).get
      status(result) mustBe NOT_FOUND
      (contentAsJson(result) \ "error").as[String] mustBe "Item not found"
    }

    "return 400 when status value is invalid" in {
      val result = route(app, FakeRequest(PATCH, "/api/v1/customers/notfound@test.com/shopping-lists/Groceries/items/Milk")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("status" -> "invalid"))).get
      status(result) mustBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] mustBe "Invalid status value"
    }

    "return 400 when status field is missing from body" in {
      val result = route(app, FakeRequest(PATCH, "/api/v1/customers/notfound@test.com/shopping-lists/Groceries/items/Milk")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("bad" -> "data"))).get
      status(result) mustBe BAD_REQUEST
    }
  }
}
