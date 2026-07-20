# Unified Currency Per Customer — Migration Plan

## Summary

Introduce a single `currency_code` on the `customers` table, set once at customer creation. All monetary records (budgets, shopping list items, expenses) inherit the customer's currency — removing `currency_code` from individual request bodies and eliminating currency mismatch issues.

## Rationale

Previously, `currency_code` was passed per-item and per-budget, allowing a customer to mix currencies (e.g. GBP budget with USD items). This creates ambiguity when calculating remaining budget and confuses the user.

**Decision:** A customer operates in a single currency. If a purchase is made in a foreign currency, the user converts it to their home currency before entry. A future UI enhancement may offer conversion assistance, but the backend always stores a single currency per customer.

## Current State

| Layer | Currency behaviour |
|-------|-------------------|
| `customers` table | No `currency_code` column |
| `Customer` model | `case class Customer(email, userId)` — no currency |
| Create customer API | `POST {"email": "..."}` — no currency |
| `customer_budgets` table | `currency_code CHAR(3) NOT NULL` per row |
| Budget create request | Client sends `currency_code` in body |
| Budget update request | Client sends `currency_code` in body |
| `shopping_list_items` table | `currency_code CHAR(3) NOT NULL` per item |
| Shopping list create request | Client sends `currency_code` per item |
| `expenses` table | `currency_code CHAR(3) NOT NULL` per row |

## Target State

| Layer | Currency behaviour |
|-------|-------------------|
| `customers` table | `currency_code CHAR(3) NOT NULL` — single source of truth |
| `Customer` model | `case class Customer(email, userId, currencyCode)` |
| Create customer API | `POST {"email": "...", "currency_code": "GBP"}` — required, set once |
| `customer_budgets` table | `currency_code` column retained for data integrity, but always matches `customer.currency_code` |
| Budget create request | `currency_code` **removed** from request body — server uses `customer.currency_code` |
| Budget update request | `currency_code` **removed** from request body — server uses `customer.currency_code` |
| `shopping_list_items` table | `currency_code` column retained, but always matches `customer.currency_code` |
| Shopping list create request | `currency_code` **removed** from item body — server uses `customer.currency_code` |
| `expenses` table | `currency_code` populated from `customer.currency_code` at insert time |

## Migration Steps

### Phase 1: Schema — Add `currency_code` to `customers`

**File:** `conf/evolutions/default/1.sql`

```sql
CREATE TABLE customers (
   email VARCHAR(320) PRIMARY KEY,
   user_id BIGINT NOT NULL,
   currency_code CHAR(3) NOT NULL CHECK (char_length(currency_code) = 3),
   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### Phase 2: Model — Update `Customer` case class

**File:** `app/models/Customer.scala`

```scala
case class Customer(email: String, userId: Long, currencyCode: String)

object Customer {
  implicit val writes: Writes[Customer] = (c: Customer) => Json.obj(
    "email" -> c.email,
    "currency_code" -> c.currencyCode
  )
}
```

### Phase 3: Create Customer API — Require `currency_code`

**Request body changes:**

```json
// Before
{"email": "user@example.com"}

// After
{"email": "user@example.com", "currency_code": "GBP"}
```

**Validation:** `currency_code` required, exactly 3 characters (ISO 4217).

**Files to update:**
- `app/controllers/CustomerController.scala` — parse `currency_code` from body
- `app/repositories/customer/SlickCustomerRepository.scala` — insert with `currency_code`
- Customer repository table definition — add column mapping

### Phase 4: Budget API — Remove `currency_code` from requests

**Budget create request body:**

```json
// Before
{"period_start": "2026-07-01", "period_end": "2026-08-01", "amount_minor": 200000, "currency_code": "GBP"}

// After
{"period_start": "2026-07-01", "period_end": "2026-08-01", "amount_minor": 200000}
```

**Budget update request body:**

```json
// Before
{"amount_minor": 250000, "currency_code": "GBP"}

// After
{"amount_minor": 250000}
```

**Files to update:**
- `app/models/requests/CustomerBudgetCreateRequest.scala` — remove `currencyCode` field and validation
- `app/models/requests/CustomerBudgetUpdateRequest.scala` — remove `currencyCode` field and validation
- `app/controllers/CustomerBudgetController.scala` — look up customer to get `currencyCode`, pass to service
- `app/services/Budget.scala` — `create` and `update` no longer accept `currencyCode` param; resolved from customer
- `app/repositories/budget/BudgetRepository.scala` — `update` signature drops `currencyCode` param (immutable once set)
- `app/repositories/budget/SlickBudgetRepository.scala` — `update` only updates `amount_minor`

**Budget response** still includes `currency_code` (read from stored record or customer):

```json
{"email": "user@example.com", "period_start": "2026-07-01", "period_end": "2026-08-01", "amount_minor": 200000, "currency_code": "GBP"}
```

### Phase 5: Shopping List API — Remove `currency_code` from item requests

**Shopping list create request items:**

```json
// Before
{"name": "Milk", "quantity": 2, "currency_code": "GBP", "unit_amount_minor": 129}

// After
{"name": "Milk", "quantity": 2, "unit_amount_minor": 129}
```

**Files to update:**
- `app/models/ShoppingListItem.scala` — remove `currencyCode` from `Reads` (not sent by client); still present in model and `Writes` (returned in responses)
- `app/controllers/ShoppingListController.scala` — look up customer to get `currencyCode`, inject into items before persistence
- `app/repositories/shoppinglist/SlickShoppingListRepository.scala` — items stored with customer's `currencyCode`

**Response** still includes `currency_code` per item (from stored record):

```json
{"name": "Milk", "quantity": 2, "currency_code": "GBP", "unit_amount_minor": 129, "line_amount_minor": 258, "status": "pending"}
```

### Phase 6: Expenses — Use `customer.currency_code` on insert

When an expense is **created** (e.g. item marked completed), the `currency_code` must be retrieved from the `customers` table within the same composed DBIO action. This ensures the expense always uses the customer's canonical currency.

When an expense is **deleted** (e.g. item reverted to pending), no currency lookup is needed — deletion targets the expense by `source_type + source_id` identity, not by currency.

**Key principle:** `currency_code` lookup happens only on the **insert path**. Delete operations use the source identity to locate the expense row.

**File:** Service layer code that composes repository DBIO actions via `DbExecutor`.

**Example — service composition using the agreed DbExecutor pattern:**

```scala
class ShoppingListServiceImpl @Inject()(
  shoppingListRepo: ShoppingListRepository,
  customerRepo: CustomerRepository,
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
      itemResult <- shoppingListRepo.updateItemStatusAction(email, listName, itemName, status)
      result <- itemResult match {
        case Right(item) if status == "completed" =>
          // Lookup customer currency on INSERT path only
          for {
            customer <- customerRepo.findByEmailAction(email)
            _ <- expenseRepo.insertAction(Expense(
              email = email,
              dayDate = item.dayDate,
              category = "groceries",
              description = s"${item.name} x${item.quantity}",
              amountMinor = item.lineAmountMinor,
              currencyCode = customer.currencyCode,  // from customer, not item
              sourceType = "shopping_list_item",
              sourceId = item.id
            ))
          } yield Right(item)

        case Right(item) if status == "pending" =>
          // DELETE path — no currency lookup needed, target by identity
          expenseRepo.deleteBySourceAction("shopping_list_item", item.id)
            .map(_ => Right(item))

        case Right(item) =>
          DBIO.successful(Right(item))

        case left @ Left(_) =>
          DBIO.successful(left)
      }
    } yield result

    dbExecutor.runTransactionally(action)
  }
}
```

**Notes:**
- `customerRepo.findByEmailAction(email)` is a new DBIO-returning method on the customer repository (follows the same action factory pattern)
- The customer lookup is inside the transaction — guarantees consistency if the customer is deleted concurrently
- After Phase 5, items no longer carry their own `currency_code` from the client — but the stored column still exists for historical reads. The service always uses `customer.currencyCode` when writing expenses.

### Phase 7: Tests — Update all layers

| Test file | Changes |
|-----------|---------|
| `test/controllers/CustomerControllerSpec.scala` | Add `currency_code` to create requests |
| `test/controllers/CustomerBudgetControllerSpec.scala` | Remove `currency_code` from budget create/update requests |
| `test/services/BudgetServiceImplSpec.scala` | Update service call signatures |
| `test/repositories/budget/SlickBudgetRepositorySpec.scala` | Update `update` calls (no `currencyCode` param) |
| `test/repositories/shoppinglist/SlickShoppingListRepositorySpec.scala` | Remove `currency_code` from item creation |
| `test/repositories/customer/SlickCustomerRepositorySpec.scala` | Add `currency_code` to customer creation |
| `test/models/requests/CustomerBudgetCreateRequestSpec.scala` | Remove `currency_code` validation tests |
| `functional-tests/` | Update all functional tests with new request shapes |

### Phase 8: Documentation — Update README

- Update API section: create customer now requires `currency_code`
- Update budget API: remove `currency_code` from request examples
- Update shopping list API: remove `currency_code` from item examples
- Update verify deployment curls
- Update data model section: `customers` table now has `currency_code`

## API Changes Summary

| Endpoint | Field | Before | After |
|----------|-------|--------|-------|
| `POST /api/v1/customers` | `currency_code` | Not present | **Required** (set once) |
| `POST /api/v1/customers/:email/budgets` | `currency_code` | Required | **Removed** |
| `PUT /api/v1/customers/:email/budgets/:period_start` | `currency_code` | Required | **Removed** |
| `POST /api/v1/customers/:email/shopping-lists` (items) | `currency_code` | Required per item | **Removed** |
| All GET responses | `currency_code` | Present | **Still present** (from stored data) |

## Decisions

1. **No currency change in V1** — `currency_code` is immutable once set at customer creation. There is no update endpoint for currency. If a customer needs to change currency, they must delete their account and recreate it. This avoids complexity around orphaned budgets/expenses in the old currency and keeps the data model simple for the initial release.
2. **GET customer response** — Should include `currency_code` so the frontend knows which currency to display. (Recommended: yes)
