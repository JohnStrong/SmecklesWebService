package models

import java.time.LocalDate

/**
 * Identifies the source table from which an expense originated.
 *
 * Each case corresponds to a specific domain table whose records can
 * trigger expense creation. The `source_id` on an expense row is a
 * foreign key into the table identified by this enum.
 *
 * @param tableName the database table name this source type maps to
 */
enum SourceType(val tableName: String):
  case ShoppingListItem extends SourceType("shopping_list_items")

object SourceType:
  /**
   * Resolves a database table name to its corresponding [[SourceType]] enum case.
   *
   * @param s the table name stored in the `source_type` DB column
   * @return `Right(sourceType)` if the name matches a known case,
   *         `Left(errorMessage)` otherwise
   */
  def fromTableName(s: String): Either[String, SourceType] =
    values.find(_.tableName == s).toRight(s"Unknown source table name: $s")

/**
 * A logical spending category that an expense belongs to.
 *
 * Categories are not tied 1:1 to source tables — multiple source types
 * may produce expenses in the same category (e.g. both `ShoppingListItem`
 * and a future `OneOff` source could be categorised as `Groceries`).
 */
enum ExpenseCategory:
  case Groceries

/**
 * Defines which source types are valid for each expense category.
 *
 * This mapping is used at insert time to validate that a given
 * `(category, sourceType)` combination is logically consistent.
 * A source type may appear in multiple categories.
 */
object ExpenseCategory:
  val applicableSources: Map[ExpenseCategory, Set[SourceType]] = Map(
    Groceries -> Set(SourceType.ShoppingListItem),
  )

  /**
   * Resolves a database string value to its corresponding [[ExpenseCategory]] enum case.
   *
   * Matching is exact (case-sensitive) against the enum case name (e.g. "Groceries").
   *
   * @param s the category string stored in the `category` DB column
   * @return `Right(category)` if the value matches a known case,
   *         `Left(errorMessage)` otherwise
   */
  def fromDBValue(s: String): Either[String, ExpenseCategory] = {
    values.find(_.toString == s).toRight(s"Unknown expense category: $s")
  }

  extension (cat: ExpenseCategory)
    /** Returns true if this category accepts expenses from the given source type. */
    def accepts(source: SourceType): Boolean =
      applicableSources.getOrElse(cat, Set.empty).contains(source)

/**
 * A realised expense — money that has actually left the customer's budget.
 *
 * @param email        the customer this expense belongs to
 * @param dayDate      the calendar day the expense was incurred
 * @param category     logical spending category (e.g. groceries, bills)
 * @param description  human-readable label (e.g. "Milk x2", "Netflix")
 * @param amountMinor  cost in minor currency units (e.g. pence, cents)
 * @param sourceType   the source table that triggered this expense
 * @param sourceId     primary key of the originating record in the source table
 * @param createdAt    epoch milliseconds UTC — when the expense was recorded
 */
case class Expense(
    email: String,
    dayDate: LocalDate,
    category: ExpenseCategory,
    description: String,
    amountMinor: Long,
    sourceType: SourceType,
    sourceId: Long,
    createdAt: Long
)
