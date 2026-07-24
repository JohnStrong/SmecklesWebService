package repositories.expense

import models.Expense
import slick.dbio.DBIO

/**
 * Repository trait for the unified expense ledger.
 *
 * The expenses table is the single source of truth for realised costs — money that
 * has actually left the budget. Entries are created indirectly by source table triggers
 * (e.g. shopping list item marked completed, subscription due date passed) and never
 * inserted directly via an API.
 *
 * All methods return [[slick.dbio.DBIO]] actions rather than [[scala.concurrent.Future]]
 * results. This follows the agreed DbExecutor + DBIO composition pattern
 * (see `docs/execution_engine/README.md`), allowing services to compose expense
 * operations with source table updates in a single atomic transaction.
 *
 * Example composition (shopping list item → expense):
 * {{{
 *   val action = for {
 *     item <- shoppingListRepo.updateItemStatusAction(email, listName, itemName, "completed")
 *     result <- item match {
 *       case Right(i) => expenseRepo.insert(Expense.fromItem(i)).map(_ => Right(i))
 *       case left     => DBIO.successful(left)
 *     }
 *   } yield result
 *
 *   dbExecutor.runTransactionally(action)
 * }}}
 *
 * @see [[docs/execution_engine/README.md]] for the full design rationale
 * @see [[docs/data_models/README.md]] for the expense table schema and trigger semantics
 */
trait ExpenseRepository {

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
  def insert(expense: Expense): DBIO[Either[String, Expense]]

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
  def delete(expense: Expense): DBIO[Either[String, Expense]]
}
