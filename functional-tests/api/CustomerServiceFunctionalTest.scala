package api

import org.scalatestplus.play.*
import play.api.libs.json.*
import play.api.test.*
import play.api.test.Helpers.*

class CustomerServiceFunctionalTest extends PlaySpec with AuthenticatedFunctionalTest {

  "CustomerController" should {

    // --- Auth tests (self-contained for customer endpoints) ---

    "return 401 when GET has no Authorization header" in {
      val request = FakeRequest(GET, "/api/v1/customers/test@example.com")
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
    }

    "return 401 when POST has no Authorization header" in {
      val request = FakeRequest(POST, "/api/v1/customers")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "x@test.com"))
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
    }

    "return 401 when token is invalid" in {
      val request = FakeRequest(GET, "/api/v1/customers/test@example.com")
        .withHeaders("Authorization" -> "Bearer invalid.token.here")
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
    }

    "return 401 when valid token email is not in the allowlist (GET)" in {
      val request = FakeRequest(GET, "/api/v1/customers/test@example.com")
        .withHeaders(authHeader("intruder@example.com"))
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
      (contentAsJson(response) \ "error").as[String] mustBe "Access denied: intruder@example.com is not authorized"
    }

    "return 401 when valid token email is not in the allowlist (POST)" in {
      val request = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader("intruder@example.com"), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "intruder@example.com"))
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
      (contentAsJson(response) \ "error").as[String] mustBe "Access denied: intruder@example.com is not authorized"
    }

    "return 401 when valid token email is not in the allowlist (DELETE)" in {
      val request = FakeRequest(DELETE, "/api/v1/customers/test@example.com")
        .withHeaders(authHeader("intruder@example.com"))
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
      (contentAsJson(response) \ "error").as[String] mustBe "Access denied: intruder@example.com is not authorized"
    }

    // --- Behaviour tests ---

    "return 409 when creating a duplicate customer" in {
      val request = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "duplicate@test.com"))
      status(route(app, request).get) mustBe CREATED

      val duplicate = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "duplicate@test.com"))
      val response = route(app, duplicate).get
      status(response) mustBe CONFLICT
    }

    "return 400 when email is missing" in {
      val request = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("name" -> "no email"))
      val response = route(app, request).get
      status(response) mustBe BAD_REQUEST
    }

    "return 400 when email is empty" in {
      val request = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> ""))
      val response = route(app, request).get
      status(response) mustBe BAD_REQUEST
    }

    "return 400 when email is null" in {
      val request = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> JsNull))
      val response = route(app, request).get
      status(response) mustBe BAD_REQUEST
    }

    "return 401 when DELETE has no Authorization header" in {
      val request = FakeRequest(DELETE, "/api/v1/customers/test@example.com")
      val response = route(app, request).get
      status(response) mustBe UNAUTHORIZED
    }

    "full lifecycle: create, get, delete, confirm gone" in {
      // Create
      val create = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "lifecycle@test.com"))
      val createResponse = route(app, create).get
      status(createResponse) mustBe CREATED
      contentAsJson(createResponse) mustBe Json.obj("email" -> "lifecycle@test.com")

      // Get — exists
      val get = FakeRequest(GET, "/api/v1/customers/lifecycle@test.com")
        .withHeaders(authHeader())
      val getResponse = route(app, get).get
      status(getResponse) mustBe OK
      contentAsJson(getResponse) mustBe Json.obj("email" -> "lifecycle@test.com")

      // Delete
      val delete = FakeRequest(DELETE, "/api/v1/customers/lifecycle@test.com")
        .withHeaders(authHeader())
      status(route(app, delete).get) mustBe NO_CONTENT

      // Get — gone
      val getAfter = FakeRequest(GET, "/api/v1/customers/lifecycle@test.com")
        .withHeaders(authHeader())
      status(route(app, getAfter).get) mustBe NOT_FOUND

      // Delete again — not found
      val deleteAgain = FakeRequest(DELETE, "/api/v1/customers/lifecycle@test.com")
        .withHeaders(authHeader())
      status(route(app, deleteAgain).get) mustBe NOT_FOUND
    }

    "cascade delete shopping lists when customer is deleted" in {
      // Create customer
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "cascade@test.com"))
      status(route(app, createCustomer).get) mustBe CREATED

      // Create shopping list for that customer
      val createList = FakeRequest(POST, "/api/v1/customers/cascade@test.com/shopping-lists")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj(
          "name" -> "Groceries",
          "period_start" -> "2026-07-01",
          "day_date" -> "2026-07-05",
          "items" -> Json.arr(Json.obj("quantity" -> 2, "currency_code" -> "GBP", "unit_amount_minor" -> 129))
        ))
      status(route(app, createList).get) mustBe CREATED

      // Verify shopping list exists
      val getListsBefore = FakeRequest(GET, "/api/v1/customers/cascade@test.com/shopping-lists")
        .withHeaders(authHeader())
      val listsBefore = contentAsJson(route(app, getListsBefore).get).as[List[JsObject]]
      listsBefore.length mustBe 1

      // Delete the customer
      val deleteCustomer = FakeRequest(DELETE, "/api/v1/customers/cascade@test.com")
        .withHeaders(authHeader())
      status(route(app, deleteCustomer).get) mustBe NO_CONTENT

      // Verify shopping lists are gone (cascade delete)
      val getListsAfter = FakeRequest(GET, "/api/v1/customers/cascade@test.com/shopping-lists")
        .withHeaders(authHeader())
      val listsAfter = contentAsJson(route(app, getListsAfter).get).as[List[JsObject]]
      listsAfter mustBe empty
    }
  }
}
