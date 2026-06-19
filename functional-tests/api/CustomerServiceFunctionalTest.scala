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
  }
}
