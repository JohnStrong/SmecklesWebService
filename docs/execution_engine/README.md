# Execution Engine: Cross-Table Transactional Design

## Foreword

This document defines the agreed design for executing cross-table database operations within a single transaction. The primary motivating use case is the **expense ledger**: when a shopping list item is marked as `completed`, an expense row must be inserted atomically in the same transaction — and removed when reverted to `pending`.

For full context on the expenses data model, source tables, and trigger semantics, see:
→ [`docs/data_models/README.md` — Expenses section](../data_models/README.md#expenses)

Key constraint from the data model:

> The status update and expense insertion happen in the **same database transaction**.

This requirement extends beyond shopping lists. Future expense sources — subscriptions, bills, rent/mortgage, one-off payments — each have their own domain table, but all write to the unified `expenses` ledger on their respective triggers. The design must support N expense sources without duplicating the expenses table definition across N repositories.

---

## Agreed Design: DbExecutor + DBIO Composition

### Concept

- **Repositories** return uncommitted `DBIO` actions (Slick's composable database IO monad) — they are action factories, not executors.
- **Services** compose multiple repository actions into a single logical operation.
- **`DbExecutor`** is an injectable trait that accepts composed actions and executes them, optionally within a transaction.

This separates three concerns:
1. **What** to do (repository actions)
2. **How to orchestrate** (service composition)
3. **How to execute** (DbExecutor — transaction boundary, connection management)

### DbExecutor Trait

```scala
package db

import slick.dbio.DBIO
import scala.concurrent.Future

trait DbExecutor {
  def run[T](action: DBIO[T]): Future[T]
  def runTransactionally[T](action: DBIO[T]): Future[T]
}
```

### Slick Implementation

```scala
package db

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SlickDbExecutor @Inject()(
  val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends DbExecutor
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  override def run[T](action: DBIO[T]): Future[T] =
    db.run(action)

  override def runTransactionally[T](action: DBIO[T]): Future[T] =
    db.run(action.transactionally)
}
```

### Guice Binding

```scala
// Module.scala
bind(classOf[DbExecutor]).to(classOf[SlickDbExecutor])
```

### Repository Layer — Action Factories

Repositories expose `DBIO`-returning methods for composition. They may also retain `Future`-based convenience methods for standalone use (which internally call `dbExecutor.run`).

```scala
trait ExpenseRepository {
  // DBIO actions for transactional composition
  def insertAction(expense: Expense): DBIO[Int]
  def deleteBySourceAction(sourceType: String, sourceId: Long): DBIO[Int]

  // Standalone Future-based methods (for direct use without composition)
  def getByEmail(email: String): Future[Either[String, List[Expense]]]
}
```

```scala
trait ShoppingListRepository {
  // DBIO action for composition
  def updateItemStatusAction(email: String, listName: String, itemName: String, status: String): DBIO[ShoppingListItem]

  // Existing Future-based methods
  def getShoppingLists(email: String): Future[Either[String, List[ShoppingList]]]
}
```

### Service Layer — Composition + Execution

The service composes repository actions and hands them to `DbExecutor`:

```scala
class ShoppingListServiceImpl @Inject()(
  shoppingListRepo: ShoppingListRepository,
  expenseRepo: ExpenseRepository,
  dbExecutor: DbExecutor
)(implicit ec: ExecutionContext) extends ShoppingListService {

  override def updateItemStatus(
    email: String,
    listName: String,
    itemName: String,
    status: String
  ): Future[Either[String, ShoppingListItem]] = {

    val action = for {
      item <- shoppingListRepo.updateItemStatusAction(email, listName, itemName, status)
      _ <- status match {
        case "completed" =>
          expenseRepo.insertAction(Expense(
            email = email,
            dayDate = item.dayDate,
            category = "groceries",
            description = s"${item.name} x${item.quantity}",
            amountMinor = item.lineAmountMinor,
            currencyCode = item.currencyCode,
            sourceType = "shopping_list_item",
            sourceId = Some(item.id)
          ))
        case "pending" =>
          expenseRepo.deleteBySourceAction("shopping_list_item", item.id)
      }
    } yield Right(item)

    dbExecutor.runTransactionally(action)
  }
}
```

### How It Scales to Multiple Expense Sources

Each source domain owns its own repository and trigger logic, but all reuse `ExpenseRepository.insertAction`:

```scala
// SubscriptionServiceImpl
val action = for {
  sub <- subscriptionRepo.markAsDueAction(subscriptionId)
  _   <- expenseRepo.insertAction(Expense.fromSubscription(sub))
} yield Right(sub)

dbExecutor.runTransactionally(action)

// BillServiceImpl
val action = for {
  bill <- billRepo.markAsPaidAction(billId)
  _    <- expenseRepo.insertAction(Expense.fromBill(bill))
} yield Right(bill)

dbExecutor.runTransactionally(action)
```

No domain repository imports the expenses table directly. `ExpenseRepository` is the single owner.

### Benefits

- **Single transaction** — status change + expense write are atomic
- **Repository boundaries preserved** — each repo owns its table(s) and exposes DBIO actions
- **No table duplication** — expenses table defined once in `ExpenseRepository`, reused by all services
- **Services decoupled from Slick internals** — no `HasDatabaseConfigProvider` on services
- **Single execution point** — `DbExecutor` is where logging, metrics, retries, or backend swaps happen
- **Testable** — mock `DbExecutor` in unit tests instead of wiring a full database
- **Explicit transaction boundaries** — `run` vs `runTransactionally` at the call site

---

## Alternatives Considered

### Option 1: Expand Repository Scope

Include the expenses table directly in each source repository (e.g. shopping list repo writes expenses).

```scala
// SlickShoppingListRepository
override def updateItemStatus(...): Future[Either[String, ShoppingListItem]] = {
  val action = (for {
    item <- // find and update item
    _ <- if (newStatus == "completed")
           expenses += ExpenseRow(...)  // expenses table defined here
         else
           expenses.filter(...).delete
  } yield Right(item)).transactionally
  db.run(action)
}
```

| Pros | Cons |
|------|------|
| Single transaction, simple to implement | Expenses table imported into every source repo |
| No additional abstractions | Violates single-responsibility — shopping list repo knows about expenses |
| | Duplicates expense logic across 4+ repos (shopping lists, subscriptions, bills, rent) |
| | Hard to change expense schema — must update every repo |

### Option 2: Service-Level Orchestration (No Single Transaction)

Each repository call is independent. The service calls them sequentially but in separate transactions.

```scala
// ShoppingListServiceImpl
for {
  item <- shoppingListRepo.updateItemStatus(...)  // transaction 1
  _    <- expenseRepo.create(Expense.fromItem(item))  // transaction 2
} yield Right(item)
```

| Pros | Cons |
|------|------|
| Clean repository boundaries | **Not atomic** — item can be marked complete with no expense if second call fails |
| Each repo owns exactly its table | Requires compensation/retry logic for consistency |
| Simple to understand | Inconsistent state visible between transactions |
| | Violates the data model requirement for same-transaction semantics |

### Option 3: DBIO Composition in Service (Without DbExecutor)

Repositories expose `DBIO` actions, but the service itself holds `HasDatabaseConfigProvider` and calls `db.run` directly.

```scala
class ShoppingListServiceImpl @Inject()(
  shoppingListRepo: ShoppingListRepository,
  expenseRepo: ExpenseRepository,
  val dbConfigProvider: DatabaseConfigProvider  // service depends on Slick
)(implicit ec: ExecutionContext) extends ShoppingListService
  with HasDatabaseConfigProvider[JdbcProfile] {

  override def updateItemStatus(...) = {
    val action = for {
      item <- shoppingListRepo.updateItemStatusAction(...)
      _    <- expenseRepo.insertAction(...)
    } yield Right(item)
    db.run(action.transactionally)
  }
}
```

| Pros | Cons |
|------|------|
| Single transaction | Every service that composes actions needs `HasDatabaseConfigProvider` |
| Repository boundaries preserved | Services coupled to Slick's profile/db internals |
| No extra abstraction layer | No central point for execution concerns (logging, metrics) |
| | Harder to mock in tests — need to wire `DatabaseConfigProvider` |

### Option 4: Dedicated Unit of Work Class

Extract cross-table operations into a dedicated coordinator class per workflow.

```scala
class ExpenseTransactionService @Inject()(
  val dbConfigProvider: DatabaseConfigProvider,
  shoppingListRepo: ShoppingListRepository,
  expenseRepo: ExpenseRepository
) extends HasDatabaseConfigProvider[JdbcProfile] {

  def markItemAndRecordExpense(...): Future[Either[String, ShoppingListItem]] = {
    val action = (for {
      item <- shoppingListRepo.updateItemStatusAction(...)
      _    <- expenseRepo.insertAction(...)
    } yield Right(item)).transactionally
    db.run(action)
  }
}
```

| Pros | Cons |
|------|------|
| Single transaction | Adds an extra layer of indirection |
| Repository boundaries preserved | Proliferates coordinator classes (one per cross-table workflow) |
| Domain service stays unaware of expenses | Transaction execution logic duplicated across coordinators |
| | Still couples each coordinator to `HasDatabaseConfigProvider` |

---

## Decision Summary

| Criterion | Option 1 | Option 2 | Option 3 | Option 4 | **Agreed (DbExecutor)** |
|-----------|----------|----------|----------|----------|--------------------------|
| Single transaction | ✅ | ❌ | ✅ | ✅ | ✅ |
| Repository boundaries | ❌ | ✅ | ✅ | ✅ | ✅ |
| Scales to N sources | ❌ | ✅ | ✅ | ⚠️ | ✅ |
| Services decoupled from Slick | ✅ | ✅ | ❌ | ❌ | ✅ |
| Central execution point | ❌ | ❌ | ❌ | ❌ | ✅ |
| Testability | ⚠️ | ✅ | ⚠️ | ⚠️ | ✅ |

The **DbExecutor** approach (agreed design) combines the transactional guarantees of Options 1/3/4 with the clean separation of Option 2, while adding a single injectable execution point that none of the alternatives provide.
