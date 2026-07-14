## Data Model (V1)

```sql
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE
);

CREATE TABLE customers (
    email VARCHAR(320) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE shopping_lists (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    name VARCHAR(30) NOT NULL,
    period_start DATE NOT NULL,          -- month bucket, e.g. 2026-07-01
    day_date DATE NOT NULL,              -- the calendar day this list is for
    UNIQUE(email, day_date, name),
    FOREIGN KEY (email) REFERENCES customers(email) ON DELETE CASCADE
);

CREATE TABLE shopping_list_items (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shopping_list_id   BIGINT NOT NULL,
    name               VARCHAR(100) NOT NULL,  -- human-readable item label (e.g. "Milk", "Bread")
    quantity           INT NOT NULL,
    currency_code      CHAR(3) NOT NULL CHECK (char_length(currency_code) = 3), -- ISO 4217
    unit_amount_minor  BIGINT NOT NULL,        -- minor units (pence/cents/etc.)
    line_amount_minor  BIGINT NOT NULL,        -- quantity * unit_amount_minor
    status             VARCHAR(20) NOT NULL DEFAULT 'pending', -- 'pending' or 'completed'
    UNIQUE(shopping_list_id, name),
    FOREIGN KEY (shopping_list_id) REFERENCES shopping_lists(id) ON DELETE CASCADE
);

CREATE INDEX shopping_list_items__by_list ON shopping_list_items (shopping_list_id);
CREATE INDEX shopping_lists__by_email_period ON shopping_lists (email, period_start);
```

### Notes on uniqueness
- Multiple lists on the same day are allowed because uniqueness includes `name` (e.g. "Groceries" and "Top-ups" on July 12).
- The same list name can appear on different days within the same month (e.g. "Weekly Groceries" on July 5, 12, 19, 26).
- `period_start` is always the 1st of the month — used for filtering/grouping lists by month.
- `day_date` is a full DATE (not just a day number) for self-contained queries and direct date comparisons.

---

## Example Inserts

Assume:
- `users` table has a user with `email = 'test@user.com'` (id = 1)
- `customers` table has `email = 'shopper@example.com'` with `user_id = 1`

### 1) Create a shopping list for July 5

```sql
INSERT INTO shopping_lists (email, name, period_start, day_date)
VALUES (
  'shopper@example.com',
  'Weekly Groceries',
  DATE '2026-07-01',
  DATE '2026-07-05'
)
RETURNING id;
```

Assume it returns `id = 10`.

### 2) Add items to that list

Milk, qty 2, £1.29 each (GBP minor units: 129 pence, line total: 2 × 129 = 258):

```sql
INSERT INTO shopping_list_items (shopping_list_id, quantity, currency_code, unit_amount_minor, line_amount_minor)
VALUES (10, 2, 'GBP', 129, 258);
```

Bananas, qty 1, £0.79:

```sql
INSERT INTO shopping_list_items (shopping_list_id, quantity, currency_code, unit_amount_minor, line_amount_minor)
VALUES (10, 1, 'GBP', 79, 79);
```

### 3) Create another "Weekly Groceries" for the following week (July 12)

Same name, different day — allowed by `UNIQUE(email, day_date, name)`:

```sql
INSERT INTO shopping_lists (email, name, period_start, day_date)
VALUES (
  'shopper@example.com',
  'Weekly Groceries',
  DATE '2026-07-01',
  DATE '2026-07-12'
)
RETURNING id;
```

Assume it returns `id = 11`.

### 4) Add different items to the July 12 list

Milk ran out again, but also need bread this week:

```sql
INSERT INTO shopping_list_items (shopping_list_id, quantity, currency_code, unit_amount_minor, line_amount_minor)
VALUES
  (11, 1, 'GBP', 129, 129),
  (11, 1, 'GBP', 85, 85);
```

### 5) Add a second list on the same day with a different name

"Top-ups" on July 12 (allowed because name differs):

```sql
INSERT INTO shopping_lists (email, name, period_start, day_date)
VALUES (
  'shopper@example.com',
  'Top-ups',
  DATE '2026-07-01',
  DATE '2026-07-12'
)
RETURNING id;
```

### 6) Query all lists for a month

```sql
SELECT id, name, day_date
FROM shopping_lists
WHERE email = 'shopper@example.com'
  AND period_start = DATE '2026-07-01'
ORDER BY day_date, name;
```

Returns:
| id | name | day_date |
|----|------|----------|
| 10 | Weekly Groceries | 2026-07-05 |
| 11 | Weekly Groceries | 2026-07-12 |
| 12 | Top-ups | 2026-07-12 |

### 7) Delete a specific list (cascades to items)

```sql
DELETE FROM shopping_lists
WHERE email = 'shopper@example.com'
  AND day_date = DATE '2026-07-05'
  AND name = 'Weekly Groceries';
```

---

## Customer Budget Data Model (Planned)

### Overview

A customer has a **monthly budget** — a fixed amount they plan to spend in a given month. Expenses are tracked independently and linked back to the budget via `period_start`. The **remaining budget** for a month is computed dynamically:

```
remaining = budget_amount - SUM(completed expenses for that period)
```

No `remaining` column is stored — it's derived at query time. This avoids stale data and race conditions when multiple expenses are marked as completed concurrently.

### Entity Relationship

```
customers 1──────┐
                 │
                 ├──→ customer_budgets (one per month per customer)
                 │
                 └──→ expenses (many per month, various sources)
                        ↑
                        │ source examples:
                        ├── shopping_list_items (when item checked off)
                        ├── subscriptions (future)
                        ├── bills (future)
                        └── one-off payments (future)
```

### Tables

#### `customer_budgets`

The spending budget for a customer over a defined period. Supports any period length (weekly, fortnightly, monthly, custom). One row per customer per budget period.

```sql
CREATE TABLE customer_budgets (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(320) NOT NULL,
    period_start   DATE NOT NULL,              -- start of budget window (e.g. 2026-07-01 or 2026-07-07)
    period_end     DATE NOT NULL,              -- end of budget window (exclusive, e.g. 2026-08-01 or 2026-07-14)
    amount_minor   BIGINT NOT NULL,            -- total budget in minor currency units
    currency_code  CHAR(3) NOT NULL CHECK (char_length(currency_code) = 3),
    UNIQUE(email, period_start),
    FOREIGN KEY (email) REFERENCES customers(email) ON DELETE CASCADE
);
```

**Period examples:**
- Monthly: `period_start = 2026-07-01`, `period_end = 2026-08-01`
- Weekly: `period_start = 2026-07-07`, `period_end = 2026-07-14`
- Fortnightly: `period_start = 2026-07-01`, `period_end = 2026-07-15`

**Validation rules (service layer):**
- `period_end` must be after `period_start`
- Budget periods for the same customer must not overlap

#### `expenses`

A unified expense log. Each row represents a single completed cost event, regardless of source. Expenses are only created when an item/event is **marked as completed** by the user.

```sql
CREATE TABLE expenses (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email              VARCHAR(320) NOT NULL,
    period_start       DATE NOT NULL,          -- month bucket (same semantics as budget)
    day_date           DATE NOT NULL,          -- the day the expense was incurred
    category           VARCHAR(50) NOT NULL,   -- e.g. 'groceries', 'subscriptions', 'bills', 'one-off'
    description        VARCHAR(100),           -- human-readable label (e.g. "Milk x2", "Netflix")
    amount_minor       BIGINT NOT NULL,        -- cost in minor currency units
    currency_code      CHAR(3) NOT NULL CHECK (char_length(currency_code) = 3),
    source_type        VARCHAR(30) NOT NULL,   -- 'shopping_list_item', 'subscription', 'bill', 'one_off'
    source_id          BIGINT,                 -- FK to the originating record (nullable for manual entries)
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (email) REFERENCES customers(email) ON DELETE CASCADE
);

CREATE INDEX expenses__by_email_period ON expenses (email, period_start);
CREATE INDEX expenses__by_category ON expenses (email, category);
```

#### `shopping_list_items` — status column addition

Add a `status` column to track whether an item has been checked off:

```sql
ALTER TABLE shopping_list_items ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'pending';
-- Valid values: 'pending', 'completed'
```

When a shopping list item is marked `completed`, an expense row is created in the `expenses` table with:
- `source_type = 'shopping_list_item'`
- `source_id = shopping_list_items.id`
- `amount_minor = line_amount_minor`
- `category` derived from the shopping list name or a user-assigned category

### Computing Remaining Budget

The remaining budget for a customer in a given period is a derived value:

```sql
SELECT
    b.amount_minor - COALESCE(SUM(e.amount_minor), 0) AS remaining_minor,
    b.currency_code,
    b.period_start,
    b.period_end
FROM customer_budgets b
LEFT JOIN expenses e
    ON e.email = b.email
    AND e.day_date >= b.period_start
    AND e.day_date < b.period_end
WHERE b.email = 'shopper@example.com'
  AND b.period_start = DATE '2026-07-01'
GROUP BY b.amount_minor, b.currency_code, b.period_start, b.period_end;
```

Expenses are matched by `day_date` falling within the budget's `[period_start, period_end)` range (start-inclusive, end-exclusive). This works for any period length — weekly, monthly, or custom.

This approach:
- **No stored `remaining`** — avoids drift between expense totals and the cached value
- **Atomic** — marking an item complete + inserting the expense can be wrapped in a single transaction
- **Auditable** — every expense has a source_type and source_id linking back to the originating record
- **Extensible** — new expense sources (subscriptions, bills) just insert rows with different `source_type`
- **Flexible periods** — same query works regardless of budget duration

### Categories

Categories are strings on the `expenses` table — no separate lookup table for V1. Examples:

| Category | Source types |
|----------|-------------|
| `groceries` | shopping_list_item |
| `household` | shopping_list_item |
| `subscriptions` | subscription |
| `bills` | bill |
| `rent` | bill |
| `one-off` | one_off |

The wants/needs classification can be derived ephemerally in the UX layer by mapping categories:
- **Needs**: groceries, bills, rent
- **Wants**: subscriptions, one-off, household (configurable per user in future)

### Example Flow

1. Customer sets July budget: £2,000 (`amount_minor = 200000`, `currency_code = 'GBP'`)
2. Customer creates "Weekly Groceries" shopping list for July 5 with items totalling £25.80
3. User goes shopping, checks off items one by one in the app
4. Each checked item → `shopping_list_items.status = 'completed'` + new row in `expenses`
5. Query remaining: `200000 - SUM(completed expenses)` = remaining pence

### Expense Creation: Synchronous Transactional Approach

Expenses are created **in the same database transaction** as the status update. No background jobs, no eventual consistency — the budget remaining is accurate the instant an item is checked off.

#### Marking an item complete

```scala
def markItemCompleted(itemId: Long): Future[Either[String, Unit]] = {
  val action = (for {
    item <- shoppingListItems.filter(_.id === itemId).result.headOption
    result <- item match {
      case Some(i) if i.status == "pending" => for {
        _ <- shoppingListItems.filter(_.id === itemId).map(_.status).update("completed")
        _ <- expenses += Expense(
          email = i.email,
          periodStart = i.periodStart,
          dayDate = i.dayDate,
          category = "groceries",
          description = Some(s"${i.quantity}x item"),
          amountMinor = i.lineAmountMinor,
          currencyCode = i.currencyCode,
          sourceType = "shopping_list_item",
          sourceId = Some(itemId)
        )
      } yield Right(())
      case Some(_) => DBIO.successful(Left("Item is already completed"))
      case None => DBIO.successful(Left("Item not found"))
    }
  } yield result).transactionally

  db.run(action)
}
```

#### Unchecking an item (reversal)

```scala
def markItemPending(itemId: Long): Future[Either[String, Unit]] = {
  val action = (for {
    _ <- shoppingListItems.filter(_.id === itemId).map(_.status).update("pending")
    _ <- expenses.filter(e => e.sourceType === "shopping_list_item" && e.sourceId === itemId).delete
  } yield Right(())).transactionally

  db.run(action)
}
```

#### Duplicate prevention

Add a unique constraint on expenses to prevent double-counting:

```sql
CREATE UNIQUE INDEX expenses__source_uniq ON expenses (source_type, source_id) WHERE source_id IS NOT NULL;
```

This guarantees the same shopping list item (or any source) can never produce two expense rows, even under concurrent requests.

#### Why this approach over a background job

| Concern | Synchronous (chosen) | Background job |
|---------|---------------------|----------------|
| Budget accuracy | Immediately consistent | Eventually consistent (stale until job runs) |
| User experience | Check off item → budget updates instantly | Check off item → budget unchanged for seconds/minutes |
| Failure handling | Both succeed or both roll back (atomic) | Partial states possible (item complete, expense missing) |
| Infrastructure | None — just a DB transaction | Scheduler, checkpoint table, retry logic |
| Complexity | Single method, easy to test | Job orchestration, duplicate detection, offset logic |
| Scale ceiling | Millions of writes/month before bottleneck | Same — both ultimately do the same INSERT |

### Design Decisions

1. **Expenses are write-once** — marking an item complete creates an expense; unchecking deletes it (or marks it `cancelled`). This keeps the ledger accurate.

2. **Single currency per budget (hard requirement)** — a customer's monthly budget has one currency. **All expenses and shopping list items for that customer and period MUST use the same currency as the budget.** The service layer enforces this: before persisting any expense or shopping list item, look up `customer_budgets` by `(email, period_start)` and reject the request if `currency_code` does not match. This is non-negotiable — without it, remaining budget calculations are meaningless. Multi-currency support would require exchange rates (deferred).

   **Enforcement point:** Service layer (not DB constraint, because it spans tables). Error response:
   ```json
   {"error": "Currency mismatch: item uses USD but customer budget for 2026-07-01 is GBP"}
   ```

3. **No pre-aggregation** — remaining is always computed live. For most users the number of monthly expenses is small enough (< 1000) that this is fast. If performance becomes an issue, a materialised view or cache can be added later.

4. **Category on expense, not on source** — the category lives on the expense row, not on the shopping list or subscription. This allows re-categorisation without touching source tables, and supports manual expense entries that have no source.


## Migration Plans

### Migration Plan 1: Introduce Shopping List Drafts

**Goal**: Allow shopping lists to exist as "drafts" — lists that belong to a month but haven't been assigned to a specific day yet. Users can create drafts as templates, then assign (copy) them to specific days.

**Prerequisite**: V1 must be shipped first (day_date NOT NULL, `UNIQUE(email, day_date, name)`).

#### Phase 1 — Schema Evolution

```sql
-- evolution 2.sql
-- !Ups
ALTER TABLE shopping_lists ALTER COLUMN day_date DROP NOT NULL;

-- PostgreSQL only (not supported in H2):
CREATE UNIQUE INDEX IF NOT EXISTS shopping_lists_draft_uniq
ON shopping_lists (email, period_start, name)
WHERE day_date IS NULL;

-- !Downs
DROP INDEX IF EXISTS shopping_lists_draft_uniq;
UPDATE shopping_lists SET day_date = period_start WHERE day_date IS NULL;
ALTER TABLE shopping_lists ALTER COLUMN day_date SET NOT NULL;
```

- `day_date = NULL` → draft (unassigned to any day)
- `day_date = <date>` → assigned to that specific day
- H2 limitation: partial unique indexes not supported; draft uniqueness enforced at application layer in dev

#### Phase 2 — Model Changes

```scala
case class ShoppingListWithItems(
  email: String,
  name: String,
  periodStart: LocalDate,
  dayDate: Option[LocalDate],   // None = draft
  items: List[ShoppingListItem]
) {
  def isDraft: Boolean = dayDate.isEmpty
}
```

Update `DecoupledShoppingList` and Slick table mapping to use `column[Option[LocalDate]]` for `day_date`.

#### Phase 3 — Repository Changes

- `findDraftsByEmail(email, periodStart)` — lists where `day_date IS NULL` for a given month
- `findAssignedByEmail(email, periodStart)` — lists where `day_date IS NOT NULL`
- Application-level uniqueness check for drafts: reject create if `(email, period_start, name)` already exists with `day_date IS NULL`

#### Phase 4 — Service Layer

New methods:

```scala
def createDraft(email: String, name: String, periodStart: LocalDate, items: List[ShoppingListItem]): Future[Either[String, ShoppingListWithItems]]
def assignToDay(email: String, name: String, periodStart: LocalDate, dayDate: LocalDate): Future[Either[String, ShoppingListWithItems]]
```

Business rules:
- `createDraft`: validates no existing draft with same `(email, period_start, name)`
- `assignToDay`: **copies** the draft to create a new assigned list (original draft persists as a reusable template)
- Rejects assignment if `(email, day_date, name)` already exists on the target day

#### Phase 5 — API Endpoints

```
POST   /api/v1/customers/:email/shopping-lists/drafts
       Body: {"name": "...", "period_start": "2026-07-01", "items": [...]}

GET    /api/v1/customers/:email/shopping-lists/drafts?period=2026-07

POST   /api/v1/customers/:email/shopping-lists/drafts/:name/assign
       Body: {"day_date": "2026-07-12"}

GET    /api/v1/customers/:email/shopping-lists?period=2026-07&include_drafts=true
```

Response format adds nullable `day_date`:

```json
{
  "email": "user@example.com",
  "name": "Weekly Groceries",
  "period_start": "2026-07-01",
  "day_date": null,
  "items": [...]
}
```

#### Phase 6 — Validation Rules

| Field | Draft | Assigned |
|-------|-------|----------|
| `name` | required, 1-20 chars | required, 1-20 chars |
| `period_start` | required, must be 1st of month | required, must be 1st of month |
| `day_date` | must be NULL / omitted | required, must fall within period_start's month |
| `items` | required, 1-50 items | required, 1-50 items |

#### Phase 7 — Tests

- Draft creation and uniqueness (one draft per name per month)
- Assignment copies draft to a specific day
- Assignment rejects duplicate `(email, day_date, name)`
- Original draft persists after assignment (reusable)
- Functional tests for all new endpoints

#### Open Questions

1. **Copy vs move**: Recommendation is copy (draft persists as reusable template). Move can be added as a `?mode=move` param later.
2. **Draft editing**: Allow PUT on drafts to update items before assignment.
3. **Recurring lists**: V3 feature — add a `recurrence` field (weekly, fortnightly) that auto-creates assigned lists from a draft at the start of each period.

---

### Migration Plan 2: Introduce Products Table (Autocomplete)

**Goal**: Provide a normalised product vocabulary per user for autocomplete suggestions when adding items to shopping lists.

#### Phase 1 — Schema Evolution

```sql
-- evolution N.sql
-- !Ups
CREATE TABLE products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, name),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

ALTER TABLE shopping_list_items ADD COLUMN product_id BIGINT NULL;
ALTER TABLE shopping_list_items ADD COLUMN product_name_snapshot TEXT NULL;
ALTER TABLE shopping_list_items
    ADD CONSTRAINT fk_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL;

CREATE INDEX shopping_list_items__by_product ON shopping_list_items (product_id);

-- !Downs
ALTER TABLE shopping_list_items DROP CONSTRAINT fk_product;
ALTER TABLE shopping_list_items DROP COLUMN product_name_snapshot;
ALTER TABLE shopping_list_items DROP COLUMN product_id;
DROP TABLE products;
```

#### Phase 2 — Model Changes

```scala
case class Product(id: Long, userId: Long, name: String)

// ShoppingListItem gains optional product reference
case class ShoppingListItem(
  quantity: Int,
  currencyCode: String,
  unitAmountMinor: Long,
  lineAmountMinor: Long,
  productId: Option[Long],
  productNameSnapshot: Option[String]
)
```

- `product_id` — nullable FK to products table; NULL for free-text items
- `product_name_snapshot` — denormalised product name at time of creation (survives product renames/deletes)

#### Phase 3 — Repository & Service

- `ProductRepository`: CRUD for user's product vocabulary
- `ProductService`: create-on-first-use (when an item is added with a new product name, auto-insert into products)
- Update `ShoppingListRepository` to persist `product_id` and `product_name_snapshot`

#### Phase 4 — API Endpoints

```
GET    /api/v1/products?q=mil         -- autocomplete search (prefix match)
POST   /api/v1/products               -- manually add a product
DELETE /api/v1/products/:id            -- remove from vocabulary
```

Shopping list item request gains optional `product_id`:

```json
{"quantity": 2, "currency_code": "GBP", "unit_amount_minor": 129, "product_id": 1}
```

If `product_id` is provided, server looks up the product name and stores it as `product_name_snapshot`. If omitted, the item is free-text only.

#### Phase 5 — Tests

- Product CRUD and uniqueness per user
- Autocomplete search (prefix, case-insensitive)
- Shopping list items with and without product_id
- ON DELETE SET NULL: deleting a product doesn't break existing list items
- product_name_snapshot preserves the name even after product rename/delete
