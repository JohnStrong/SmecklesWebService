package api

import org.scalatestplus.play.*
import play.api.libs.json.*
import play.api.test.*
import play.api.test.Helpers.*

class CustomerBudgetFunctionalTest extends PlaySpec with AuthenticatedFunctionalTest with CustomerFixture {

  private def validBudgetBody(
    periodStart: String = "2026-07-01",
    periodEnd: String = "2026-08-01",
    amountMinor: Long = 200000L,
    currencyCode: String = "GBP"
  ) = Json.obj(
    "period_start" -> periodStart,
    "period_end" -> periodEnd,
    "amount_minor" -> amountMinor,
    "currency_code" -> currencyCode
  )

  "CustomerBudgetController" should {

    // --- Auth tests ---

    "return 401 when GET has no Authorization header" in {
      val request = FakeRequest(GET, "/api/v1/customers/test@example.com/budgets")
      status(route(app, request).get) mustBe UNAUTHORIZED
    }

    "return 401 when POST has no Authorization header" in {
      val request = FakeRequest(POST, "/api/v1/customers/test@example.com/budgets")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(validBudgetBody())
      status(route(app, request).get) mustBe UNAUTHORIZED
    }

    "return 401 when PUT has no Authorization header" in {
      val request = FakeRequest(PUT, "/api/v1/customers/test@example.com/budgets/2026-07-01")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("amount_minor" -> 250000, "currency_code" -> "GBP"))
      status(route(app, request).get) mustBe UNAUTHORIZED
    }

    "return 401 when DELETE has no Authorization header" in {
      val request = FakeRequest(DELETE, "/api/v1/customers/test@example.com/budgets/2026-07-01")
      status(route(app, request).get) mustBe UNAUTHORIZED
    }

    "return 401 when token email is not in the allowlist" in {
      val request = FakeRequest(GET, "/api/v1/customers/test@example.com/budgets")
        .withHeaders(authHeader("intruder@example.com"))
      val result = route(app, request).get
      status(result) mustBe UNAUTHORIZED
      (contentAsJson(result) \ "error").as[String] mustBe "Access denied: intruder@example.com is not authorized"
    }

    // --- Create budget (POST) ---

    "create a budget and return 201 with the budget" in {
      createCustomer("budget-create@test.com")

      val request = FakeRequest(POST, "/api/v1/customers/budget-create@test.com/budgets")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validBudgetBody())
      val result = route(app, request).get

      status(result) mustBe CREATED
      val json = contentAsJson(result)
      (json \ "email").as[String] mustBe "budget-create@test.com"
      (json \ "period_start").as[String] mustBe "2026-07-01"
      (json \ "period_end").as[String] mustBe "2026-08-01"
      (json \ "amount_minor").as[Long] mustBe 200000L
      (json \ "currency_code").as[String] mustBe "GBP"
    }

    "return 409 when budget period overlaps with an existing budget" in {
      createCustomer("budget-overlap@test.com")

      val first = FakeRequest(POST, "/api/v1/customers/budget-overlap@test.com/budgets")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validBudgetBody(periodStart = "2026-07-01", periodEnd = "2026-08-01"))
      status(route(app, first).get) mustBe CREATED

      // Overlapping period
      val overlapping = FakeRequest(POST, "/api/v1/customers/budget-overlap@test.com/budgets")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validBudgetBody(periodStart = "2026-07-15", periodEnd = "2026-08-15"))
      val result = route(app, overlapping).get

      status(result) mustBe CONFLICT
      (contentAsJson(result) \ "error").as[String] must include("overlap")
    }

    "return 400 when period_end is before period_start" in {
      createCustomer("budget-invalid@test.com")

      val request = FakeRequest(POST, "/api/v1/customers/budget-invalid@test.com/budgets")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validBudgetBody(periodStart = "2026-08-01", periodEnd = "2026-07-01"))
      val result = route(app, request).get

      status(result) mustBe BAD_REQUEST
    }

    "return 400 when required fields are missing" in {
      createCustomer("budget-missing@test.com")

      val request = FakeRequest(POST, "/api/v1/customers/budget-missing@test.com/budgets")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("bad" -> "data"))
      val result = route(app, request).get

      status(result) mustBe BAD_REQUEST
    }

    "return 400 when currency_code is not exactly 3 characters" in {
      createCustomer("budget-currency@test.com")

      val request = FakeRequest(POST, "/api/v1/customers/budget-currency@test.com/budgets")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validBudgetBody(currencyCode = "GB"))
      val result = route(app, request).get

      status(result) mustBe BAD_REQUEST
    }

    // --- Get budgets (GET) ---

    "return empty list when customer has no budgets" in {
      createCustomer("budget-empty@test.com")

      val request = FakeRequest(GET, "/api/v1/customers/budget-empty@test.com/budgets")
        .withHeaders(authHeader())
      val result = route(app, request).get

      status(result) mustBe OK
      contentAsJson(result).as[List[JsObject]] mustBe empty
    }

    "return all budgets for a customer" in {
      createCustomer("budget-getall@test.com")

      status(route(app, FakeRequest(POST, "/api/v1/customers/budget-getall@test.com/budgets")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validBudgetBody(periodStart = "2026-07-01", periodEnd = "2026-08-01"))).get) mustBe CREATED

      status(route(app, FakeRequest(POST, "/api/v1/customers/budget-getall@test.com/budgets")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validBudgetBody(periodStart = "2026-08-01", periodEnd = "2026-09-01"))).get) mustBe CREATED

      val request = FakeRequest(GET, "/api/v1/customers/budget-getall@test.com/budgets")
        .withHeaders(authHeader())
      val result = route(app, request).get

      status(result) mustBe OK
      contentAsJson(result).as[List[JsObject]].length mustBe 2
    }

    // --- Update budget (PUT) ---

    "update budget amount and return 200" in {
      createCustomer("budget-update@test.com")

      status(route(app, FakeRequest(POST, "/api/v1/customers/budget-update@test.com/budgets")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validBudgetBody(amountMinor = 200000L))).get) mustBe CREATED

      val updateRequest = FakeRequest(PUT, "/api/v1/customers/budget-update@test.com/budgets/2026-07-01")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("amount_minor" -> 250000, "currency_code" -> "GBP"))
      val result = route(app, updateRequest).get

      status(result) mustBe OK
      (contentAsJson(result) \ "amount_minor").as[Long] mustBe 250000L
    }

    "return 404 when updating a budget that does not exist" in {
      createCustomer("budget-notfound@test.com")

      val request = FakeRequest(PUT, "/api/v1/customers/budget-notfound@test.com/budgets/2026-07-01")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("amount_minor" -> 250000, "currency_code" -> "GBP"))
      val result = route(app, request).get

      status(result) mustBe NOT_FOUND
    }

    // --- Delete budget (DELETE) ---

    "delete a budget and return 204" in {
      createCustomer("budget-delete@test.com")

      status(route(app, FakeRequest(POST, "/api/v1/customers/budget-delete@test.com/budgets")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(validBudgetBody())).get) mustBe CREATED

      val deleteResult = route(app, FakeRequest(DELETE, "/api/v1/customers/budget-delete@test.com/budgets/2026-07-01")
        .withHeaders(authHeader())).get
      status(deleteResult) mustBe NO_CONTENT

      // Confirm gone
      val getResult = route(app, FakeRequest(GET, "/api/v1/customers/budget-delete@test.com/budgets")
        .withHeaders(authHeader())).get
      contentAsJson(getResult).as[List[JsObject]] mustBe empty
    }

    "return 204 when deleting a budget that does not exist (idempotent)" in {
      createCustomer("budget-del-idem@test.com")

      val result = route(app, FakeRequest(DELETE, "/api/v1/customers/budget-del-idem@test.com/budgets/2026-07-01")
        .withHeaders(authHeader())).get
      status(result) mustBe NO_CONTENT
    }

    "return 400 when period_start in path is not a valid date" in {
      val result = route(app, FakeRequest(DELETE, "/api/v1/customers/test@test.com/budgets/not-a-date")
        .withHeaders(authHeader())).get
      status(result) mustBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] must include("Invalid date format")
    }
  }
}
