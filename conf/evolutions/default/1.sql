-- !Ups

CREATE TABLE users(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE
);

CREATE TABLE customers (
   email VARCHAR(320) PRIMARY KEY,
   user_id BIGINT NOT NULL,
   currency_code CHAR(3) NOT NULL CHECK (char_length(currency_code) = 3), -- ISO 4217 (e.g. GBP, EUR, USD)
   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE customer_budgets (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email          VARCHAR(320) NOT NULL,      -- customer email
  period_start   DATE NOT NULL,              -- start of budget window (e.g. 2026-07-01 or 2026-07-07)
  period_end     DATE NOT NULL,              -- end of budget window (exclusive, e.g. 2026-08-01 or 2026-07-14)
  amount_minor   BIGINT NOT NULL,            -- total budget in minor currency units (1,234.56 → 123456)
  UNIQUE(email, period_start),
  FOREIGN KEY (email) REFERENCES customers(email) ON DELETE CASCADE
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
     id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
     shopping_list_id       BIGINT NOT NULL,
     name                   VARCHAR(100) NOT NULL,  -- human-readable item label (e.g. "Milk", "Bread")
     quantity               INT NOT NULL,
     unit_amount_minor      BIGINT NOT NULL,        -- minor units (pence/cents/etc.) e.g. 12.34$ -> 1234
     line_amount_minor      BIGINT NOT NULL,        -- quantity * unit_amount_minor
     status                 VARCHAR(20) NOT NULL DEFAULT 'pending', -- 'pending' or 'completed'
     UNIQUE(shopping_list_id, name),
     FOREIGN KEY (shopping_list_id) REFERENCES shopping_lists(id) ON DELETE CASCADE
);
CREATE INDEX shopping_list_items__by_list ON shopping_list_items (shopping_list_id);
CREATE INDEX shopping_lists__by_email_period ON shopping_lists (email, period_start);

CREATE TABLE expenses (
      id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
      email              VARCHAR(320) NOT NULL,
      day_date           DATE NOT NULL,          -- the day the expense was incurred/due
      category           VARCHAR(50) NOT NULL,   -- e.g. 'groceries', 'subscriptions', 'bills', 'one-off'
      description        VARCHAR(100),           -- human-readable label (e.g. "Milk x2", "Netflix")
      amount_minor       BIGINT NOT NULL,        -- cost in minor currency units
      currency_code      CHAR(3) NOT NULL CHECK (char_length(currency_code) = 3),
      source_type        VARCHAR(30) NOT NULL,   -- 'shopping_list_item', 'subscription', 'bill', 'one_off'
      source_id          BIGINT NOT NULL,         -- FK to the originating record in the source table
      created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(source_type, source_id),
      FOREIGN KEY (email) REFERENCES customers(email) ON DELETE CASCADE
);

CREATE INDEX expenses__by_email_day ON expenses (email, day_date);
CREATE INDEX expenses__by_category ON expenses (email, category);

-- !Downs
DROP TABLE expenses;
DROP TABLE shopping_list_items;
DROP TABLE shopping_lists;
DROP TABLE customer_budgets;
DROP TABLE customers;
DROP TABLE users;