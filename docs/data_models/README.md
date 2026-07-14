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