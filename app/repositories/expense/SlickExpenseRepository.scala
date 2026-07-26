package repositories.expense

import models.{Expense, ExpenseCategory, SourceType}
import repositories.DataIntegrityException
import slick.dbio.DBIO

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

class SlickExpenseRepository extends ExpenseRepository {

  /**
   * Inserts a new expense into the ledger.
   *
   * Called when a source record triggers a realised cost (e.g. shopping list item
   * marked as completed). The `UNIQUE(source_type, source_id)` constraint prevents
   * duplicate entries — attempting to insert an expense for an already-recorded source
   * returns a Left with an error message.
   *
   * This action is intended to be composed within a transaction alongside the source
   * table update that triggered it.
   *
   * @param expense the expense to record, with all fields populated by the caller
   *                (including `createdAt` as epoch milliseconds UTC)
   * @return a DBIO action yielding Right with the persisted expense on success,
   *         or Left with an error message if the insert violates constraints
   *         (e.g. duplicate source_type + source_id)
   */
  override def insert(expense: Expense): DBIO[Either[String, Expense]] = ???

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
