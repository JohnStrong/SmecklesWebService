package controllers

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import play.api.test.*
import play.api.test.Helpers.*
import play.api.libs.json.*
import services.BudgetService
import models.Budget
import helpers.StubAuth

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class CustomerBudgetControllerSpec extends AnyWordSpec with Matchers {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  private def createFixture() = {
    val mockService = mock(classOf[BudgetService])
    val controller = new CustomerBudgetController(Helpers.stubControllerComponents(), StubAuth.action, mockService)
    (controller, mockService)
  }

  private val testBudget = Budget(
    email = "user@example.com",
    periodStart = LocalDate.of(2026, 7, 1),
    periodEnd = LocalDate.of(2026, 8, 1),
    amountMinor = 200000L,
    currencyCode = "GBP"
  )

  private def validCreateBody(
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

  "getBudgets" should {

    "return 200 with list of budgets" in {
      val (controller, mockService) = createFixture()
      when(mockService.getBudgets("user@example.com"))
        .thenReturn(Future.successful(Right(List(testBudget))))

      val result = controller.getBudgets("user@example.com").apply(FakeRequest())

      status(result) shouldBe OK
      val json = contentAsJson(result).as[List[JsObject]]
      json.length shouldBe 1
      (json.head \ "email").as[String] shouldBe "user@example.com"
      (json.head \ "period_start").as[String] shouldBe "2026-07-01"
      (json.head \ "period_end").as[String] shouldBe "2026-08-01"
      (json.head \ "amount_minor").as[Long] shouldBe 200000L
      (json.head \ "currency_code").as[String] shouldBe "GBP"
    }

    "return 200 with empty list when no budgets exist" in {
      val (controller, mockService) = createFixture()
      when(mockService.getBudgets("user@example.com"))
        .thenReturn(Future.successful(Right(List.empty)))

      val result = controller.getBudgets("user@example.com").apply(FakeRequest())

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.arr()
    }

    "return 500 when service returns an error" in {
      val (controller, mockService) = createFixture()
      when(mockService.getBudgets("user@example.com"))
        .thenReturn(Future.successful(Left("Database error")))

      val result = controller.getBudgets("user@example.com").apply(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "error").as[String] should include("Database error")
    }
  }

  "create" should {

    "return 201 with created budget on success" in {
      val (controller, mockService) = createFixture()
      when(mockService.create(any[Budget]()))
        .thenReturn(Future.successful(Right(testBudget)))

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(validCreateBody())
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe CREATED
      val json = contentAsJson(result)
      (json \ "email").as[String] shouldBe "user@example.com"
      (json \ "period_start").as[String] shouldBe "2026-07-01"
      (json \ "period_end").as[String] shouldBe "2026-08-01"
      (json \ "amount_minor").as[Long] shouldBe 200000L
      (json \ "currency_code").as[String] shouldBe "GBP"
    }

    "return 409 when budget period overlaps" in {
      val (controller, mockService) = createFixture()
      when(mockService.create(any[Budget]()))
        .thenReturn(Future.successful(Left("Budget period overlaps with an existing budget (2026-07-01 to 2026-08-01)")))

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(validCreateBody())
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe CONFLICT
      (contentAsJson(result) \ "error").as[String] should include("overlaps")
    }

    "return 400 when request body is invalid" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("bad" -> "data"))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] shouldBe "Invalid request format"
    }

    "return 400 when period_end is before period_start" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(validCreateBody(periodStart = "2026-08-01", periodEnd = "2026-07-01"))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when currency_code is not 3 characters" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(validCreateBody(currencyCode = "GB"))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when amount_minor is negative" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(validCreateBody(amountMinor = -1))
      val result = controller.create("user@example.com").apply(request)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "update" should {

    "return 200 with updated budget on success" in {
      val (controller, mockService) = createFixture()
      val updated = testBudget.copy(amountMinor = 250000L)
      when(mockService.update("user@example.com", LocalDate.of(2026, 7, 1), 250000L, "GBP"))
        .thenReturn(Future.successful(Right(updated)))

      val request = FakeRequest(PUT, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("amount_minor" -> 250000, "currency_code" -> "GBP"))
      val result = controller.update("user@example.com", LocalDate.of(2026, 7, 1)).apply(request)

      status(result) shouldBe OK
      (contentAsJson(result) \ "amount_minor").as[Long] shouldBe 250000L
      (contentAsJson(result) \ "currency_code").as[String] shouldBe "GBP"
    }

    "return 404 when budget not found" in {
      val (controller, mockService) = createFixture()
      when(mockService.update("user@example.com", LocalDate.of(2026, 7, 1), 250000L, "GBP"))
        .thenReturn(Future.successful(Left("No budget found for period starting 2026-07-01")))

      val request = FakeRequest(PUT, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("amount_minor" -> 250000, "currency_code" -> "GBP"))
      val result = controller.update("user@example.com", LocalDate.of(2026, 7, 1)).apply(request)

      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "error").as[String] should include("No budget found")
    }

    "return 400 when request body is invalid" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(PUT, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("bad" -> "data"))
      val result = controller.update("user@example.com", LocalDate.of(2026, 7, 1)).apply(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when amount_minor is negative" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(PUT, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("amount_minor" -> -1, "currency_code" -> "GBP"))
      val result = controller.update("user@example.com", LocalDate.of(2026, 7, 1)).apply(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when currency_code is not 3 characters" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(PUT, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("amount_minor" -> 250000, "currency_code" -> "GB"))
      val result = controller.update("user@example.com", LocalDate.of(2026, 7, 1)).apply(request)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
