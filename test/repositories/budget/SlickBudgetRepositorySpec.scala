package repositories.budget

import models.Budget
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import repositories.RepositoryTestFixture
import repositories.customer.SlickCustomerRepository

import java.time.LocalDate

class SlickBudgetRepositorySpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with EitherValues
  with GuiceOneAppPerTest
  with RepositoryTestFixture {

  implicit val patience: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(100, Millis)
  )

  private def repository = app.injector.instanceOf[SlickBudgetRepository]
  protected def customerRepository = app.injector.instanceOf[SlickCustomerRepository]

  private val julyBudget = Budget(
    email = "test@example.com",
    periodStart = LocalDate.of(2026, 7, 1),
    periodEnd = LocalDate.of(2026, 8, 1),
    amountMinor = 200000L,
    currencyCode = "GBP"
  )

  private def withCustomer(test: => Any): Unit = withCustomer(julyBudget.email)(test)

  "create" should {

    "insert a budget when no other budgets exist for the customer" in withCustomer {
      val result = repository.create(julyBudget).futureValue

      result.value shouldBe julyBudget
    }

    "allow adjacent budgets (end of one equals start of next)" in withCustomer {
      repository.create(julyBudget).futureValue

      val augustBudget = julyBudget.copy(
        periodStart = LocalDate.of(2026, 8, 1),
        periodEnd = LocalDate.of(2026, 9, 1)
      )
      val result = repository.create(augustBudget).futureValue

      result.value shouldBe augustBudget
    }

    "return Left when new budget overlaps at the end of existing" in withCustomer {
      // Existing: 07-01 to 08-01
      repository.create(julyBudget).futureValue

      // New: 07-15 to 08-15
      val overlapping = julyBudget.copy(
        periodStart = LocalDate.of(2026, 7, 15),
        periodEnd = LocalDate.of(2026, 8, 15)
      )
      val result = repository.create(overlapping).futureValue

      result.left.value should include("overlaps")
    }

    "return Left when new budget overlaps at the start of existing" in withCustomer {
      // Existing: 07-02 to 07-09
      val existing = julyBudget.copy(
        periodStart = LocalDate.of(2026, 7, 2),
        periodEnd = LocalDate.of(2026, 7, 9)
      )
      repository.create(existing).futureValue

      // New: 07-01 to 07-08
      val overlapping = julyBudget.copy(
        periodStart = LocalDate.of(2026, 7, 1),
        periodEnd = LocalDate.of(2026, 7, 8)
      )
      val result = repository.create(overlapping).futureValue

      result.left.value should include("overlaps")
    }

    "return Left when new budget is entirely inside existing (existing: 07-01 to 07-07, new: 07-02 to 07-06)" in withCustomer {
      val existing = julyBudget.copy(
        periodStart = LocalDate.of(2026, 7, 1),
        periodEnd = LocalDate.of(2026, 7, 7)
      )
      repository.create(existing).futureValue

      val inside = julyBudget.copy(
        periodStart = LocalDate.of(2026, 7, 2),
        periodEnd = LocalDate.of(2026, 7, 6)
      )
      val result = repository.create(inside).futureValue

      result.left.value should include("overlaps")
    }

    "return Left when new budget entirely contains existing (existing: 07-02 to 07-07, new: 07-01 to 07-08)" in withCustomer {
      val existing = julyBudget.copy(
        periodStart = LocalDate.of(2026, 7, 2),
        periodEnd = LocalDate.of(2026, 7, 7)
      )
      repository.create(existing).futureValue

      val containing = julyBudget.copy(
        periodStart = LocalDate.of(2026, 7, 1),
        periodEnd = LocalDate.of(2026, 7, 8)
      )
      val result = repository.create(containing).futureValue

      result.left.value should include("overlaps")
    }

    "return Left when exact same period is created twice" in withCustomer {
      repository.create(julyBudget).futureValue

      val result = repository.create(julyBudget).futureValue

      result.left.value should include("overlaps")
    }
  }
}
