package services

import models.Budget
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import repositories.budget.BudgetRepository

import java.time.LocalDate
import scala.concurrent.Future

class BudgetServiceImplSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private val testBudget = Budget(
    email = "user@example.com",
    periodStart = LocalDate.of(2026, 7, 1),
    periodEnd = LocalDate.of(2026, 8, 1),
    amountMinor = 200000L,
    currencyCode = "GBP"
  )

  private def freshService() = {
    val mockRepo = mock(classOf[BudgetRepository])
    (new BudgetServiceImpl(mockRepo), mockRepo)
  }

  "getBudgets" should {

    "return Right with list of budgets" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.get("user@example.com"))
        .thenReturn(Future.successful(Right(List(testBudget))))

      val result = service.getBudgets("user@example.com").futureValue

      result shouldBe Right(List(testBudget))
    }

    "return Right with empty list when no budgets exist" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.get("user@example.com"))
        .thenReturn(Future.successful(Right(List.empty)))

      val result = service.getBudgets("user@example.com").futureValue

      result shouldBe Right(List.empty)
    }

    "propagate failure when repo throws" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.get("user@example.com"))
        .thenReturn(Future.failed(new RuntimeException("Connection reset")))

      val result = service.getBudgets("user@example.com").failed.futureValue

      result.getMessage shouldBe "Connection reset"
    }
  }

  "create" should {

    "return Right with created budget on success" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.create(any[Budget]()))
        .thenReturn(Future.successful(Right(testBudget)))

      val result = service.create(testBudget).futureValue

      result shouldBe Right(testBudget)
    }

    "return Left when budget period overlaps" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.create(any[Budget]()))
        .thenReturn(Future.successful(Left("Budget period overlaps with an existing budget (2026-07-01 to 2026-08-01)")))

      val result = service.create(testBudget).futureValue

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("overlaps")
    }

    "propagate failure when repo throws" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.create(any[Budget]()))
        .thenReturn(Future.failed(new RuntimeException("Connection reset")))

      val result = service.create(testBudget).failed.futureValue

      result.getMessage shouldBe "Connection reset"
    }
  }
}
