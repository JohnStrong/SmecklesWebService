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

`currency_code` exists **only** on the `customers` table. No other table stores it. The frontend fetches the customer once, caches the currency, and uses it for display (e.g. showing £ or $ next to amounts). API responses for budgets, shopping lists, and expenses return amounts only — no `currency_code` field.

| Layer | Currency behaviour |
|-------|-------------------|
| `customers` table | `currency_code CHAR(3) NOT NULL` — **the only place currency lives** |
| `Customer` model | `case class Customer(email, userId, currencyCode)` |
| Create customer API | `POST {"email": "...", "currency_code": "GBP"}` — required, immutable |
| GET customer API | Returns `{"email": "...", "currency_code": "GBP"}` — frontend caches this |
| `customer_budgets` table | **No `currency_code` column** — amounts are implicitly in customer's currency |
| `shopping_list_items` table | **No `currency_code` column** — amounts are implicitly in customer's currency |
| `expenses` table | **No `currency_code` column** — amounts are implicitly in customer's currency |
| Budget/shopping list/expense API responses | **No `currency_code`** — frontend already knows from customer |

### Why the frontend resolves currency

The UI makes a single call to `GET /api/v1/customers/:email` on login, stores `currency_code` in its internal state, and uses it to render the correct symbol (£, $, €) next to all amounts. This eliminates:

- `currency_code` columns on `customer_budgets`, `shopping_list_items`, and `expenses`
- Joins or lookups on the read path to include currency in every response
- Redundant data repeated across hundreds of rows

The backend only needs the customer's currency on the **expense insert path** (to validate consistency within the transaction).

## End-to-End Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. CREATE CUSTOMER                                                          │
│                                                                             │
│    POST /api/v1/customers                                                   │
│    {"email": "alice@example.com", "currency_code": "GBP"}                   │
│                                                                             │
│    → 201 {"email": "alice@example.com", "currency_code": "GBP"}             │
│                                                                             │
│    ┌──────────────────────────────────────────┐                             │
│    │ customers                                 │                             │
│    │ email: alice@example.com                  │                             │
│    │ currency_code: GBP  ← only place it lives │                             │
│    └──────────────────────────────────────────┘                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. CREATE BUDGET (no currency_code in request)                              │
│                                                                             │
│    POST /api/v1/customers/alice@example.com/budgets                         │
│    {"period_start": "2026-07-01", "period_end": "2026-08-01",               │
│     "amount_minor": 200000}                                                 │
│                                                                             │
│    → 201 {"email": "alice@example.com", "period_start": "2026-07-01",       │
│            "period_end": "2026-08-01", "amount_minor": 200000}              │
│                                                                             │
│    ┌──────────────────────────────────────────┐                             │
│    │ customer_budgets                          │                             │
│    │ email: alice@example.com                  │                             │
│    │ period_start: 2026-07-01                  │                             │
│    │ period_end: 2026-08-01                    │                             │
│    │ amount_minor: 200000                      │                             │
│    │ (no currency_code column)                 │                             │
│    └──────────────────────────────────────────┘                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. CREATE SHOPPING LIST WITH ITEMS (no currency_code in request)             │
│                                                                             │
│    POST /api/v1/customers/alice@example.com/shopping-lists                   │
│    {"name": "Groceries", "period_start": "2026-07-01",                      │
│     "day_date": "2026-07-05",                                               │
│     "items": [                                                              │
│       {"name": "Milk", "quantity": 2, "unit_amount_minor": 129},            │
│       {"name": "Bread", "quantity": 1, "unit_amount_minor": 100}            │
│     ]}                                                                      │
│                                                                             │
│    → 201 {"email": "alice@example.com", "name": "Groceries", ...            │
│            "items": [                                                       │
│              {"name": "Milk", "quantity": 2, "unit_amount_minor": 129,       │
│               "line_amount_minor": 258, "status": "pending"},               │
│              {"name": "Bread", "quantity": 1, "unit_amount_minor": 100,      │
│               "line_amount_minor": 100, "status": "pending"}                │
│            ]}                                                               │
│                                                                             │
│    ┌──────────────────────────────────────────┐                             │
│    │ shopping_list_items                       │                             │
│    │ name: Milk, quantity: 2                   │                             │
│    │ unit_amount_minor: 129                    │                             │
│    │ line_amount_minor: 258                    │                             │
│    │ status: pending                           │                             │
│    │ (no currency_code column)                 │                             │
│    └──────────────────────────────────────────┘                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. MARK ITEM COMPLETED → EXPENSE INSERTED (single transaction)              │
│                                                                             │
│    PATCH /api/v1/customers/alice@example.com/shopping-lists/Groceries/       │
│          items/Milk                                                         │
│    {"status": "completed"}                                                  │
│                                                                             │
│    → 200 {"name": "Milk", "quantity": 2, "unit_amount_minor": 129,          │
│            "line_amount_minor": 258, "status": "completed"}                 │
│                                                                             │
│    ┌─── SINGLE TRANSACTION (via DbExecutor.runTransactionally) ───────┐     │
│    │                                                                   │     │
│    │  1. UPDATE shopping_list_items SET status='completed'              │     │
│    │     WHERE list=Groceries AND name=Milk                            │     │
│    │                                                                   │     │
│    │  2. SELECT currency_code FROM customers                           │     │
│    │     WHERE email='alice@example.com'  → GBP                        │     │
│    │                                                                   │     │
│    │  3. INSERT INTO expenses                                          │     │
│    │     (email, day_date, category, description,                      │     │
│    │      amount_minor, source_type, source_id)                        │     │
│    │     VALUES ('alice@example.com', '2026-07-05', 'groceries',       │     │
│    │      'Milk x2', 258, 'shopping_list_item', <item_id>)             │     │
│    │                                                                   │     │
│    └───────────────────────────────────────────────────────────────────┘     │
│                                                                             │
│    ┌──────────────────────────────────────────┐                             │
│    │ expenses                                  │                             │
│    │ email: alice@example.com                  │                             │
│    │ day_date: 2026-07-05                      │                             │
│    │ category: groceries                       │                             │
│    │ description: Milk x2                      │                             │
│    │ amount_minor: 258                         │                             │
│    │ source_type: shopping_list_item           │                             │
│    │ source_id: 1                              │                             │
│    │ (no currency_code column)                 │                             │
│    └──────────────────────────────────────────┘                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 5. GET REMAINING BUDGET                                                     │
│                                                                             │
│    GET /api/v1/customers/alice@example.com/budgets/2026-07-01/remaining      │
│                                                                             │
│    Server logic:                                                            │
│    ┌───────────────────────────────────────────────────────────────────┐    │
│    │  budget = SELECT amount_minor FROM customer_budgets                │    │
│    │          WHERE email = 'alice@example.com'                         │    │
│    │            AND period_start = '2026-07-01'                         │    │
│    │  → 200000                                                         │    │
│    │                                                                   │    │
│    │  spent = SELECT COALESCE(SUM(amount_minor), 0) FROM expenses      │    │
│    │          WHERE email = 'alice@example.com'                         │    │
│    │            AND day_date >= '2026-07-01'                            │    │
│    │            AND day_date < '2026-08-01'                             │    │
│    │  → 258                                                            │    │
│    │                                                                   │    │
│    │  remaining = 200000 - 258 = 199742                                │    │
│    └───────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│    → 200 {"period_start": "2026-07-01", "period_end": "2026-08-01",         │
│            "amount_minor": 200000, "spent_minor": 258,                      │
│            "remaining_minor": 199742}                                       │
│                                                                             │
│    Frontend renders: "£1,997.42 remaining" (£ from cached customer.GBP)     │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key insight:** Currency is never stored on budgets, items, or expenses. The frontend fetches `currency_code` once from `GET /api/v1/customers/:email` and uses it for all display formatting. The backend works purely in minor currency units — currency-agnostic integers.

## Migration Steps

### Phase 1: Schema — Add `currency_code` to `customers`, remove from other tables

**File:** `conf/evolutions/default/1.sql`

```sql
CREATE TABLE customers (
   email VARCHAR(320) PRIMARY KEY,
   user_id BIGINT NOT NULL,
   currency_code CHAR(3) NOT NULL CHECK (char_length(currency_code) = 3),
   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

Remove `currency_code` column from:
- `customer_budgets`
- `shopping_list_items`
- `expenses`

These tables store amounts in minor units only. The currency is always the customer's currency, resolved by joining on `email` when needed (which is only on the expense insert path — not on reads).

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
- `app/models/requests/CustomerBudgetUpdateRequest.scala` — remove `currencyCode` field (becomes just `amount_minor`)
- `app/controllers/CustomerBudgetController.scala` — no currency handling needed
- `app/services/Budget.scala` — `create` and `update` no longer accept `currencyCode` param
- `app/models/Budget.scala` — remove `currencyCode` field from model and writes
- `app/repositories/budget/BudgetRepository.scala` — `update` signature drops `currencyCode` param
- `app/repositories/budget/SlickBudgetRepository.scala` — `update` only updates `amount_minor`; remove column mapping

**Budget response no longer includes `currency_code`:**

```json
{"email": "user@example.com", "period_start": "2026-07-01", "period_end": "2026-08-01", "amount_minor": 200000}
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
- `app/models/ShoppingListItem.scala` — remove `currencyCode` from model, `Reads`, and `Writes`
- `app/controllers/ShoppingListController.scala` — no currency handling needed
- `app/repositories/shoppinglist/SlickShoppingListRepository.scala` — remove `currency_code` column mapping from items table

**Response no longer includes `currency_code` per item:**

```json
{"name": "Milk", "quantity": 2, "unit_amount_minor": 129, "line_amount_minor": 258, "status": "pending"}
```

### Phase 6: Expenses — No `currency_code` column

Since `currency_code` is removed from the `expenses` table entirely, the expense insert no longer needs to look up the customer's currency. All amounts are stored as minor units in the customer's implicit currency.

When an expense is **created** (e.g. item marked completed), the insert only needs the amount, source identity, and metadata — no currency resolution.

When an expense is **deleted** (e.g. item reverted to pending), deletion targets by `source_type + source_id` identity.

**Example — service composition using the agreed DbExecutor pattern:**

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
      itemResult <- shoppingListRepo.updateItemStatusAction(email, listName, itemName, status)
      result <- itemResult match {
        case Right(item) if status == "completed" =>
          // No currency lookup needed — amounts are currency-agnostic integers
          expenseRepo.insertAction(Expense(
            email = email,
            dayDate = item.dayDate,
            category = "groceries",
            description = s"${item.name} x${item.quantity}",
            amountMinor = item.lineAmountMinor,
            sourceType = "shopping_list_item",
            sourceId = item.id
          )).map(_ => Right(item))

        case Right(item) if status == "pending" =>
          // DELETE path — target by source identity
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
- No `customerRepo` dependency needed for this operation
- The expense row has no `currency_code` column — it's implicitly the customer's currency
- The transaction guarantees atomicity: status update + expense insert/delete succeed or fail together

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
| `POST /api/v1/customers` | `currency_code` | Not present | **Required** (set once, immutable) |
| `GET /api/v1/customers/:email` | `currency_code` | Not present | **Returned** (frontend caches this) |
| `POST /api/v1/customers/:email/budgets` | `currency_code` | Required | **Removed** from request and response |
| `PUT /api/v1/customers/:email/budgets/:period_start` | `currency_code` | Required | **Removed** from request and response |
| `GET /api/v1/customers/:email/budgets` | `currency_code` | Returned per budget | **Removed** from response |
| `POST /api/v1/customers/:email/shopping-lists` (items) | `currency_code` | Required per item | **Removed** from request and response |
| `GET /api/v1/customers/:email/shopping-lists` (items) | `currency_code` | Returned per item | **Removed** from response |
| `PATCH .../items/:item_name` (update status) | `currency_code` | Returned | **Removed** from response |

## Decisions

1. **No currency change in V1** — `currency_code` is immutable once set at customer creation. There is no update endpoint for currency. If a customer needs to change currency, they must delete their account and recreate it. This avoids complexity around orphaned budgets/expenses in the old currency and keeps the data model simple for the initial release.
2. **GET customer response** — Includes `currency_code`. The frontend fetches this once on login and caches it for all display formatting.
3. **No `currency_code` on any table except `customers`** — all monetary amounts are stored as minor unit integers. The currency is implicit from the customer. This eliminates redundant data, removes joins on read paths, and prevents mismatch bugs.
