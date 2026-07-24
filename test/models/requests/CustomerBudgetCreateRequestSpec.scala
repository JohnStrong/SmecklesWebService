package models.requests

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.*

class CustomerBudgetCreateRequestSpec extends AnyWordSpec with Matchers {

  private def validBody(
    periodStart: JsValue = JsString("2026-07-01"),
    periodEnd: JsValue = JsString("2026-08-01"),
    amountMinor: JsValue = JsNumber(200000)
  ) = Json.obj(
    "period_start" -> periodStart,
    "period_end" -> periodEnd,
    "amount_minor" -> amountMinor
  )

  "CustomerBudgetCreateRequest" should {

    "deserialize successfully with valid fields" in {
      val result = validBody().validate[CustomerBudgetCreateRequest]
      result shouldBe a[JsSuccess[_]]
      val req = result.get
      req.periodStart.toString shouldBe "2026-07-01"
      req.periodEnd.toString shouldBe "2026-08-01"
      req.amountMinor shouldBe 200000L
    }

    "accept a weekly budget period" in {
      val result = validBody(periodStart = JsString("2026-07-07"), periodEnd = JsString("2026-07-14"))
        .validate[CustomerBudgetCreateRequest]
      result shouldBe a[JsSuccess[_]]
    }

    "accept zero for amount_minor" in {
      val result = validBody(amountMinor = JsNumber(0)).validate[CustomerBudgetCreateRequest]
      result shouldBe a[JsSuccess[_]]
    }

    "fail when period_start is missing" in {
      val json = validBody().as[JsObject] - "period_start"
      json.validate[CustomerBudgetCreateRequest] shouldBe a[JsError]
    }

    "fail when period_end is missing" in {
      val json = validBody().as[JsObject] - "period_end"
      json.validate[CustomerBudgetCreateRequest] shouldBe a[JsError]
    }

    "fail when amount_minor is missing" in {
      val json = validBody().as[JsObject] - "amount_minor"
      json.validate[CustomerBudgetCreateRequest] shouldBe a[JsError]
    }

    "fail when amount_minor is negative" in {
      val result = validBody(amountMinor = JsNumber(-1)).validate[CustomerBudgetCreateRequest]
      result shouldBe a[JsError]
    }

    "fail when period_start is not a valid date" in {
      val result = validBody(periodStart = JsString("not-a-date")).validate[CustomerBudgetCreateRequest]
      result shouldBe a[JsError]
    }

    "fail when period_end is not a valid date" in {
      val result = validBody(periodEnd = JsString("banana")).validate[CustomerBudgetCreateRequest]
      result shouldBe a[JsError]
    }

    "fail when period_start is after period_end" in {
      val result = validBody(periodStart = JsString("2026-08-01"), periodEnd = JsString("2026-07-01"))
        .validate[CustomerBudgetCreateRequest]
      result shouldBe a[JsError]
    }

    "fail when period_start equals period_end" in {
      val result = validBody(periodStart = JsString("2026-07-01"), periodEnd = JsString("2026-07-01"))
        .validate[CustomerBudgetCreateRequest]
      result shouldBe a[JsError]
    }
  }
}
