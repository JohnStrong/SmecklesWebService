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

    "create a new customer and get it" in {
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "functional@test.com"))
      val response = route(app, createCustomer).get
      status(response) mustBe CREATED
      contentAsJson(response) mustBe Json.obj("email" -> "functional@test.com")

      val getCustomerById = FakeRequest(GET, "/api/v1/customers/functional@test.com")
        .withHeaders(authHeader())
      val getResponse = route(app, getCustomerById).get
      status(getResponse) mustBe OK
      contentAsJson(getResponse) mustBe Json.obj("email" -> "functional@test.com")
    }

    "return 404 when getting a non-existent customer" in {
      val request = FakeRequest(GET, "/api/v1/customers/nonexistent@test.com")
        .withHeaders(authHeader())
      val response = route(app, request).get
      status(response) mustBe NOT_FOUND
      (contentAsJson(response) \ "error").as[String] must include("nonexistent@test.com")
    }

    "return 409 when creating a duplicate customer" in {
      val request = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "duplicate@test.com"))
      route(app, request).get

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
  }
}
