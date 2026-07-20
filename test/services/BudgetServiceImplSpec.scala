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

  "update" should {

    "return Right with updated budget on success" in {
      val (service, mockRepo) = freshService()
      val updated = testBudget.copy(amountMinor = 250000L)
      when(mockRepo.update("user@example.com", LocalDate.of(2026, 7, 1), 250000L, "GBP"))
        .thenReturn(Future.successful(Right(updated)))

      val result = service.update("user@example.com", LocalDate.of(2026, 7, 1), 250000L, "GBP").futureValue

      result shouldBe Right(updated)
    }

    "return Left when budget not found" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.update("user@example.com", LocalDate.of(2026, 9, 1), 100000L, "GBP"))
        .thenReturn(Future.successful(Left("No budget found for period starting 2026-09-01")))

      val result = service.update("user@example.com", LocalDate.of(2026, 9, 1), 100000L, "GBP").futureValue

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("No budget found")
    }

    "propagate failure when repo throws" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.update("user@example.com", LocalDate.of(2026, 7, 1), 250000L, "GBP"))
        .thenReturn(Future.failed(new RuntimeException("Connection reset")))

      val result = service.update("user@example.com", LocalDate.of(2026, 7, 1), 250000L, "GBP").failed.futureValue

      result.getMessage shouldBe "Connection reset"
    }
  }

  "delete" should {

    "return Right(()) on successful deletion" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.delete("user@example.com", LocalDate.of(2026, 7, 1)))
        .thenReturn(Future.successful(Right(())))

      val result = service.delete("user@example.com", LocalDate.of(2026, 7, 1)).futureValue

      result shouldBe Right(())
    }

    "return Right(()) when budget does not exist (idempotent)" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.delete("user@example.com", LocalDate.of(2026, 9, 1)))
        .thenReturn(Future.successful(Right(())))

      val result = service.delete("user@example.com", LocalDate.of(2026, 9, 1)).futureValue

      result shouldBe Right(())
    }

    "propagate failure when repo throws" in {
      val (service, mockRepo) = freshService()
      when(mockRepo.delete("user@example.com", LocalDate.of(2026, 7, 1)))
        .thenReturn(Future.failed(new RuntimeException("Connection reset")))

      val result = service.delete("user@example.com", LocalDate.of(2026, 7, 1)).failed.futureValue

      result.getMessage shouldBe "Connection reset"
    }
  }
}
