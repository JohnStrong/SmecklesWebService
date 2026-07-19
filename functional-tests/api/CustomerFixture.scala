package api

import play.api.libs.json.*
import play.api.test.*
import play.api.test.Helpers.*
import play.api.Application

/**
 * Provides helper methods for creating customers in functional tests.
 * Mix into any functional test that needs a pre-existing customer.
 */
trait CustomerFixture { self: AuthenticatedFunctionalTest with org.scalatestplus.play.PlaySpec =>

  /** Creates a customer and asserts 201. Call at the start of any test that needs a customer. */
  protected def createCustomer(email: String): Unit = {
    val request = FakeRequest(POST, "/api/v1/customers")
      .withHeaders(authHeader(), "Content-Type" -> "application/json")
      .withBody(Json.obj("email" -> email))
    status(route(app, request).get) mustBe CREATED
  }
}
