package models

import java.time.LocalDate

/**
 * The outcome of an item status update operation in the shopping list repository.
 *
 * Distinguishes between a genuine state transition and a no-op so that
 * downstream logic (e.g. expense creation/deletion) only fires when the
 * status actually changed. This prevents duplicate expense inserts when
 * the same status is applied idempotently.
 */
enum StatusUpdateResult:

  /**
   * The item's status was changed from one value to another.
   *
   * @param item the updated item with the new status applied
   * @param listDayDate the `day_date` of the parent shopping list — used as the
   *                    expense `dayDate` when recording a realised cost
   */
  case Changed(item: ShoppingListItem, listDayDate: LocalDate)

  /**
   * The item already had the requested status — no database write occurred.
   *
   * @param item the item in its current (unchanged) state
   */
  case Unchanged(item: ShoppingListItem)

  /**
   * The item or its parent shopping list could not be found.
   */
  case NotFound
