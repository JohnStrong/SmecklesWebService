-- !Ups

CREATE TABLE users(
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
    status VARCHAR(20) NOT NULL DEFAULT 'pending', -- items marked pending or completed
    UNIQUE(email, day_date, name),
    FOREIGN KEY (email) REFERENCES customers(email) ON DELETE CASCADE
);

CREATE TABLE shopping_list_items (
     id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
     shopping_list_id       BIGINT NOT NULL,
     quantity               INT NOT NULL,
     currency_code          CHAR(3) NOT NULL CHECK (char_length(currency_code)=3), -- ISO 4217
     unit_amount_minor      BIGINT NOT NULL,        -- minor units (pence/cents/etc.) e.g. 12.34$ -> 1234
     line_amount_minor      BIGINT NOT NULL,        -- quantity * unit_amount_minor
     FOREIGN KEY (shopping_list_id) REFERENCES shopping_lists(id) ON DELETE CASCADE
);
CREATE INDEX shopping_list_items__by_list ON shopping_list_items (shopping_list_id);
CREATE INDEX shopping_lists__by_email_period ON shopping_lists (email, period_start);

-- !Downs
DROP TABLE shopping_list_items;
DROP TABLE shopping_lists;
DROP TABLE customers;
DROP TABLE users;