package repositories.expense

import models.{Expense, ExpenseCategory, SourceType}
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.db.slick.DatabaseConfigProvider
import repositories.{DataIntegrityException, RepositoryTestFixture}
import repositories.customer.SlickCustomerRepository
import slick.jdbc.JdbcProfile

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class SlickExpenseRepositorySpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with EitherValues
  with GuiceOneAppPerTest
  with RepositoryTestFixture {

  implicit val patience: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(100, Millis)
  )

  protected def customerRepository = app.injector.instanceOf[SlickCustomerRepository]

  private def dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]

  private def repository = new SlickExpenseRepository(dbConfigProvider)

  private def dbExecutor = app.injector.instanceOf[db.DbExecutor]

  private val testExpense = Expense(
    email = "test@example.com",
    dayDate = LocalDate.of(2026, 7, 5),
    category = ExpenseCategory.Groceries,
    description = "Milk x2",
    amountMinor = 258L,
    sourceType = SourceType.ShoppingListItem,
    sourceId = 1L,
    createdAt = 1753470000000L
  )

  "toExpenseRow" should {

    "convert an Expense to an ExpenseRow with correct field mappings" in {
      val row = testExpense.toExpenseRow

      row.id shouldBe 0L
      row.email shouldBe "test@example.com"
      row.dayDate shouldBe java.sql.Date.valueOf(LocalDate.of(2026, 7, 5))
      row.category shouldBe "Groceries"
      row.description shouldBe "Milk x2"
      row.amountMinor shouldBe 258L
      row.sourceType shouldBe "shopping_list_items"
      row.sourceId shouldBe 1L
      row.createdAt shouldBe 1753470000000L
    }

    "set id to 0 for auto-increment" in {
      val row = testExpense.toExpenseRow
      row.id shouldBe 0L
    }

    "map sourceType to its tableName" in {
      val row = testExpense.toExpenseRow
      row.sourceType shouldBe SourceType.ShoppingListItem.tableName
    }

    "map category to its string representation" in {
      val row = testExpense.toExpenseRow
      row.category shouldBe "Groceries"
    }
  }

  "ExpenseRow.toExpense" should {

    "convert an ExpenseRow back to an Expense with correct field mappings" in {
      val row = ExpenseRow(
        id = 42L,
        email = "test@example.com",
        dayDate = java.sql.Date.valueOf(LocalDate.of(2026, 7, 5)),
        category = "Groceries",
        description = "Milk x2",
        amountMinor = 258L,
        sourceType = "shopping_list_items",
        sourceId = 1L,
        createdAt = 1753470000000L
      )

      val expense = ExpenseRow.toExpense(row)

      expense.email shouldBe "test@example.com"
      expense.dayDate shouldBe LocalDate.of(2026, 7, 5)
      expense.category shouldBe ExpenseCategory.Groceries
      expense.description shouldBe "Milk x2"
      expense.amountMinor shouldBe 258L
      expense.sourceType shouldBe SourceType.ShoppingListItem
      expense.sourceId shouldBe 1L
      expense.createdAt shouldBe 1753470000000L
    }

    "throw DataIntegrityException for unknown source type" in {
      val row = ExpenseRow(
        id = 1L,
        email = "test@example.com",
        dayDate = java.sql.Date.valueOf(LocalDate.of(2026, 7, 5)),
        category = "Groceries",
        description = "test",
        amountMinor = 100L,
        sourceType = "nonexistent_table",
        sourceId = 1L,
        createdAt = 1753470000000L
      )

      val ex = the[DataIntegrityException] thrownBy ExpenseRow.toExpense(row)
      ex.message should include("nonexistent_table")
    }

    "throw DataIntegrityException for unknown expense category" in {
      val row = ExpenseRow(
        id = 1L,
        email = "test@example.com",
        dayDate = java.sql.Date.valueOf(LocalDate.of(2026, 7, 5)),
        category = "InvalidCategory",
        description = "test",
        amountMinor = 100L,
        sourceType = "shopping_list_items",
        sourceId = 1L,
        createdAt = 1753470000000L
      )

      val ex = the[DataIntegrityException] thrownBy ExpenseRow.toExpense(row)
      ex.message should include("InvalidCategory")
    }
  }

  "insert" should {

    "insert an expense and return Right with the expense" in withCustomer(testExpense.email) {
      val result = dbExecutor.run(repository.insert(testExpense)).futureValue

      result.value shouldBe testExpense

      // Verify persisted — re-inserting the same source should fail
      val ex = dbExecutor.run(repository.insert(testExpense)).failed.futureValue
      ex shouldBe a[java.sql.SQLException]
    }

    "throw constraint violation when inserting duplicate source_type + source_id" in withCustomer(testExpense.email) {
      dbExecutor.run(repository.insert(testExpense)).futureValue

      val duplicate = testExpense.copy(
        amountMinor = 999L,
        description = "different description"
      )

      val ex = dbExecutor.run(repository.insert(duplicate)).failed.futureValue
      ex shouldBe a[java.sql.SQLException]
    }

    "allow inserting expenses with different source_id for same source_type" in withCustomer(testExpense.email) {
      dbExecutor.run(repository.insert(testExpense)).futureValue

      val different = testExpense.copy(sourceId = 2L)
      val result = dbExecutor.run(repository.insert(different)).futureValue

      result.value shouldBe different
    }
  }

  "delete" should {

    "delete an existing expense by source_type and source_id and return Right" in withCustomer(testExpense.email) {
      dbExecutor.run(repository.insert(testExpense)).futureValue

      val result = dbExecutor.run(repository.delete(testExpense)).futureValue

      result.value shouldBe testExpense

      // Verify the expense is gone — re-insert should succeed
      val reInsert = dbExecutor.run(repository.insert(testExpense)).futureValue
      reInsert.value shouldBe testExpense
    }

    "return Right when expense does not exist (idempotent)" in withCustomer(testExpense.email) {
      val result = dbExecutor.run(repository.delete(testExpense)).futureValue

      result.value shouldBe testExpense
    }

    "only delete the expense matching the specific source_type + source_id" in withCustomer(testExpense.email) {
      dbExecutor.run(repository.insert(testExpense)).futureValue

      val other = testExpense.copy(sourceId = 2L)
      dbExecutor.run(repository.insert(other)).futureValue

      // Delete only the first one
      dbExecutor.run(repository.delete(testExpense)).futureValue

      // Verify the deleted expense is gone — re-insert should succeed
      val reInsert = dbExecutor.run(repository.insert(testExpense)).futureValue
      reInsert.value shouldBe testExpense

      // Verify the other expense is still there — re-insert should fail
      val ex = dbExecutor.run(repository.insert(other)).failed.futureValue
      ex shouldBe a[java.sql.SQLException]
    }

    "allow re-inserting after delete (complete → pending → complete cycle)" in withCustomer(testExpense.email) {
      dbExecutor.run(repository.insert(testExpense)).futureValue
      dbExecutor.run(repository.delete(testExpense)).futureValue

      // Should be able to insert again after deletion
      val result = dbExecutor.run(repository.insert(testExpense)).futureValue
      result.value shouldBe testExpense
    }
  }
}
