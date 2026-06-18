-- !Ups

CREATE TABLE users(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE
);

CREATE TABLE customers (
   email VARCHAR(320) PRIMARY KEY,
   user_id BIGINT NOT NULL,
   FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE shopping_lists (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    name VARCHAR(30) NOT NULL,
    UNIQUE(email),
    FOREIGN KEY (email) REFERENCES customers(email)
);

CREATE TABLE shopping_list_items (
     id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
     shopping_list_id BIGINT NOT NULL,
     name VARCHAR(30) NOT NULL,
     quantity INT NOT NULL,
     FOREIGN KEY (shopping_list_id) REFERENCES shopping_lists(id)
);

-- !Downs
DROP TABLE shopping_list_items;
DROP TABLE shopping_lists;
DROP TABLE customers;
DROP TABLE users;