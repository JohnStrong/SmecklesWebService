package api

import org.scalatestplus.play.*
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.*
import play.api.test.*
import play.api.test.Helpers.*

class CustomerServiceFunctionalTest extends PlaySpec with GuiceOneAppPerSuite {

  "CustomerController" should {

    "create a new customer and get it" in {
      // create the customer
      val createCustomer = FakeRequest(POST, "/api/v1/customers")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "functional@test.com"))
      val response = route(app, createCustomer).get
      status(response) mustBe CREATED
      contentAsJson(response) mustBe Json.obj("email" -> "functional@test.com")

      // get the customer details
      val getCustomerById = FakeRequest(GET, "/api/v1/customers/functional@test.com")
        .withHeaders("Content-Type" -> "application/json")
      val getResponse = route(app, getCustomerById).get
      status(getResponse) mustBe OK
      contentAsJson(getResponse) mustBe Json.obj("email" -> "functional@test.com")
    }

    "return 404 when getting a non-existent customer" in {
      val request = FakeRequest(GET, "/api/v1/customers/nonexistent@test.com")
      val response = route(app, request).get
      status(response) mustBe NOT_FOUND
      (contentAsJson(response) \ "error").as[String] must include("nonexistent@test.com")
    }

    "return 409 when creating a duplicate customer" in {
      val request = FakeRequest(POST, "/api/v1/customers")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "duplicate@test.com"))

      // create first time
      val first = route(app, request).get
      status(first) mustBe CREATED

      // create again - should conflict
      val second = route(app, request).get
      status(second) mustBe CONFLICT
      (contentAsJson(second) \ "error").as[String] must include("duplicate@test.com")
    }

    "return 400 when email is missing" in {
      val request = FakeRequest(POST, "/api/v1/customers")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj())
      val response = route(app, request).get
      status(response) mustBe BAD_REQUEST
      (contentAsJson(response) \ "error").as[String] mustBe "Email is required"
    }

    "return 400 when email is empty" in {
      val request = FakeRequest(POST, "/api/v1/customers")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> ""))
      val response = route(app, request).get
      status(response) mustBe BAD_REQUEST
      (contentAsJson(response) \ "error").as[String] mustBe "Email is required"
    }

    "return 400 when email is null" in {
      val request = FakeRequest(POST, "/api/v1/customers")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> JsNull))
      val response = route(app, request).get
      status(response) mustBe BAD_REQUEST
      (contentAsJson(response) \ "error").as[String] mustBe "Email is required"
    }
  }
}
