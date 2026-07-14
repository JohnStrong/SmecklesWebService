## Data model (no `shopping_list_days` table)

```sql
-- Optional: for autocomplete / normalized vocabulary
CREATE TABLE products (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id     UUID NOT NULL,
  name        TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, name)
);

-- A shopping list is either:
-- - an unassigned draft for a month: day_date IS NULL
-- - assigned to a specific day: day_date NOT NULL
CREATE TABLE shopping_lists (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id        UUID NOT NULL,
  period_start  DATE NOT NULL,      -- month bucket, e.g. 2026-07-01
  day_date      DATE NULL,          -- NULL = unassigned draft, else specific calendar day
  name           TEXT NOT NULL,     -- user-defined label (supports multiple lists per day)

  status         TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('active','cancelled')),

  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Multiple lists per same day allowed (different name).
  -- Enforce uniqueness only when the same "slot" matches including name.
  CONSTRAINT shopping_lists_day_name_uniq
    UNIQUE (user_id, day_date, name),

  -- But also allow multiple month drafts (different name) when day_date is NULL.
  -- Since UNIQUE treats NULLs as distinct in Postgres, we add partial unique indexes instead.
  -- (The constraint above is fine, but you still want correct enforcement for NULL day_date.)
);

-- Uniqueness for drafts (day_date IS NULL) — name is required and day_date not set.
CREATE UNIQUE INDEX shopping_lists_user_period_name_uniq
ON shopping_lists (user_id, period_start, name)
WHERE day_date IS NULL;

-- Items: money snapshot + per-item store string
CREATE TABLE shopping_list_items (
  id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  shopping_list_id      BIGINT NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,

  product_id             BIGINT NULL REFERENCES products(id) ON DELETE SET NULL,
  product_name_snapshot TEXT NOT NULL,

  quantity               NUMERIC(12,3) NOT NULL CHECK (quantity > 0),

  store_name_snapshot    TEXT NULL,      -- user's string; NULL if not provided

  currency_code          CHAR(3) NOT NULL CHECK (char_length(currency_code)=3), -- ISO 4217
  unit_amount_minor      BIGINT NOT NULL, -- minor units (pence/cents/etc.)
  line_amount_minor      BIGINT NOT NULL, -- snapshot total

  created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX shopping_list_items__by_list ON shopping_list_items (shopping_list_id);
CREATE INDEX shopping_list_items__by_product ON shopping_list_items (product_id);
CREATE INDEX shopping_lists__by_user_period
ON shopping_lists (user_id, period_start);
```

### Notes on uniqueness
- Multiple lists on the same day are allowed because uniqueness includes `name`.
- Drafts for a month (`day_date IS NULL`) are unique by `(user_id, period_start, name)` via the partial unique index.

---

## Example inserts

Assume:
- `user_id = '11111111-1111-1111-1111-111111111111'`
- `products` table has `Milk`

### 1) Product vocabulary
```sql
INSERT INTO products (user_id, name)
VALUES ('11111111-1111-1111-1111-111111111111', 'Milk')
RETURNING id;
```

Assume it returns `product_id = 1`.

### 2) Create an unassigned draft shopping list for July
```sql
INSERT INTO shopping_lists (user_id, period_start, day_date, name)
VALUES (
  '11111111-1111-1111-1111-111111111111',
  DATE '2026-07-01',
  NULL,
  'July Draft - Groceries'
)
RETURNING id;
```

Assume `shopping_list.id = 10`.

### 3) Add items to that draft (money + store per item)
Milk from Tesco, qty 2, £1.29 each:
- GBP minor units: £1.29 = 129 pence
- line total: 2 * 129 = 258

```sql
INSERT INTO shopping_list_items (
  shopping_list_id,
  product_id,
  product_name_snapshot,
  quantity,
  store_name_snapshot,
  currency_code,
  unit_amount_minor,
  line_amount_minor
) VALUES (
  10,
  1,
  'Milk',
  2,
  'Tesco',
  'GBP',
  129,
  258
);
```

Free-text item “Bananas” with store not provided:
```sql
INSERT INTO shopping_list_items (
  shopping_list_id,
  product_id,
  product_name_snapshot,
  quantity,
  store_name_snapshot,
  currency_code,
  unit_amount_minor,
  line_amount_minor
) VALUES (
  10,
  NULL,
  'Bananas',
  1,
  NULL,
  'GBP',
  79,
  79
);
```

### 4) Later assign the list to a specific day (e.g., July 12)
```sql
UPDATE shopping_lists
SET day_date = DATE '2026-07-12',
    updated_at = now()
WHERE id = 10;
```

### 5) Add another list on the same day (allowed because `name` differs)
```sql
INSERT INTO shopping_lists (user_id, period_start, day_date, name)
VALUES (
  '11111111-1111-1111-1111-111111111111',
  DATE '2026-07-01',
  DATE '2026-07-12',
  'Top-ups'
)
RETURNING id;
```

Assume it returns `shopping_list.id = 11`.

Add Milk from Dunness 2 on that day, qty 1, £1.35:
```sql
INSERT INTO shopping_list_items (
  shopping_list_id,
  product_id,
  product_name_snapshot,
  quantity,
  store_name_snapshot,
  currency_code,
  unit_amount_minor,
  line_amount_minor
) VALUES (
  11,
  1,
  'Milk',
  1,
  'Dunness 2',
  'GBP',
  135,
  135
);
```

### 6) “Delete the day” behavior (day cancellation without losing history)
Instead of deleting, cancel lists assigned to that day:
```sql
UPDATE shopping_lists
SET status = 'cancelled',
    updated_at = now()
WHERE user_id = '11111111-1111-1111-1111-111111111111'
  AND day_date = DATE '2026-07-12'
  AND status = 'active';
```

Then in reporting, filter `status='active'` (or treat `cancelled` as excluded).

---

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
