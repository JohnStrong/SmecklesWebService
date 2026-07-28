package repositories.expense

import models.{Expense, ExpenseCategory, SourceType}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import repositories.DataIntegrityException
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

case class ExpenseRow(
    id: Long,
    email: String,
    dayDate: java.sql.Date,
    category: String,
    description: String,
    amountMinor: Long,
    sourceType: String,
    sourceId: Long,
    createdAt: Long
)

extension (e: Expense) {
  def toExpenseRow: ExpenseRow = ExpenseRow(
    id = 0L, // Auto-incremented by the database
    email = e.email,
    dayDate = java.sql.Date.valueOf(e.dayDate),
    category = e.category.toString,
    description = e.description,
    amountMinor = e.amountMinor,
    sourceType = e.sourceType.tableName,
    sourceId = e.sourceId,
    createdAt = e.createdAt
  )
}

object ExpenseRow {
  extension [T](e: Either[String, T]) {
    def orThrowDataIntegrity: T =
      e.fold(msg => throw DataIntegrityException(msg), identity)
  }

  def toExpense(expenseRow: ExpenseRow): Expense = {
    val sourceType = SourceType.fromTableName(expenseRow.sourceType).orThrowDataIntegrity
    val expenseCategory = ExpenseCategory.fromDBValue(expenseRow.category).orThrowDataIntegrity
    Expense(
      email = expenseRow.email,
      dayDate = expenseRow.dayDate.toLocalDate,
      category = ExpenseCategory.valueOf(expenseRow.category),
      description = expenseRow.description,
      amountMinor = expenseRow.amountMinor,
      sourceType = sourceType,
      sourceId = expenseRow.sourceId,
      createdAt = expenseRow.createdAt
    )
  }
}

class SlickExpenseRepository(
  val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
  extends ExpenseRepository
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  private class ExpensesTable(tag: Tag) extends Table[ExpenseRow](tag, "expenses") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email = column[String]("email")
    def dayDate = column[java.sql.Date]("day_date")
    def category = column[String]("category")
    def description = column[String]("description")
    def amountMinor = column[Long]("amount_minor")
    def sourceType = column[String]("source_type")
    def sourceId = column[Long]("source_id")
    def createdAt = column[Long]("created_at")

    def uniqueSourceConstraint = index("idx_unique_source", (sourceType, sourceId), unique = true)

    def *  = (id, email, dayDate, category, description, amountMinor, sourceType, sourceId, createdAt) <> (ExpenseRow.apply, ExpenseRow.unapply)
  }
  private val expenses = TableQuery[ExpensesTable]

  /**
   * Inserts a new expense into the ledger (optimistic, no pre-check).
   *
   * Called when a source record triggers a realised cost (e.g. shopping list item
   * marked as completed). The `UNIQUE(source_type, source_id)` constraint prevents
   * duplicate entries — attempting to insert an expense for an already-recorded source
   * will throw a constraint violation exception, rolling back the enclosing transaction.
   *
   * No SELECT is performed before the INSERT because:
   *  - The DB checks the unique index on INSERT regardless, so a pre-check is redundant work.
   *
   * This action is intended to be composed within a transaction alongside the source
   * table update that triggered it.
   *
   * @param expense the expense to record, with all fields populated by the caller
   *                (including `createdAt` as epoch milliseconds UTC)
   * @return a DBIO action yielding Right with the persisted expense on success
   */
  override def insert(expense: Expense): DBIO[Either[String, Expense]] =
    (expenses += expense.toExpenseRow).map(_ => Right(expense))

  /**
   * Deletes an expense from the ledger by matching on `source_type` and `source_id`.
   *
   * Called when a source record reverts its trigger (e.g. shopping list item status
   * changed back to pending). The expense is located by its source identity — not by
   * the expense's own ID — because the caller knows the source record but not the
   * generated expense ID.
   *
   * The operation is idempotent — deleting an expense that does not exist returns
   * Right with the passed expense (no-op).
   *
   * This action is intended to be composed within a transaction alongside the source
   * table update that triggered the reversal.
   *
   * @param expense the expense to remove, identified by `sourceType` and `sourceId`
   * @return a DBIO action yielding Right with the expense on success (deleted or
   *         already absent), or Left with an error message on unexpected failure
   */
  override def delete(expense: Expense): DBIO[Either[String, Expense]] = ???
}
