# Shopping List App

A web application for managing shopping lists — built as a personal, independent alternative to existing shopping list apps.

## Overview

A backend + frontend service that allows users to:

- Create and manage multiple shopping lists
- Add, remove, and check off items
- Name lists by purpose (e.g. groceries, hardware store, vinted)
- Access their shopping lists from anywhere

## Tech Stack

| Technology | Role | Why |
|---|---|---|
| [Scala 3](https://docs.scala-lang.org/) | Language | Expressive, type-safe, functional-first JVM language with excellent concurrency support |
| [sbt](https://www.scala-sbt.org/) | Build tool | The standard Scala build tool — handles compilation, dependency management, testing, and running |
| [Play Framework 3.x](https://www.playframework.com/) | Web framework | Builds the web application and handles REST APIs. Stateless, non-blocking architecture. Hot-reloads code changes in dev mode so you see edits instantly without restarting the server |
| [Apache Pekko](https://pekko.apache.org/) | Concurrency & streaming | Actor-based message passing for highly concurrent state management. Streams API for reactive frontend↔backend data flow. Handles backpressure, fault tolerance, and supervision out of the box |
| [H2](https://h2database.com/) | Database (dev) | Lightweight in-memory SQL database for local development — zero setup, auto-creates on startup. May be swapped for PostgreSQL in production |
| [ScalaTest](https://www.scalatest.org/) | Testing | The standard Scala testing framework — flexible DSL styles, rich matchers, integrates with mocking libraries |

### Why Apache Pekko over Akka?

Akka changed to a Business Source License (BSL) in 2022, making it non-free for production use. [Apache Pekko](https://pekko.apache.org/) is the community-maintained open-source fork (Apache 2.0 licensed), hosted by the Apache Software Foundation. Play Framework 3.x is already built on Pekko rather than Akka.

### Key Features of the Stack

- **Hot reload** — Play recompiles and reloads on every request in dev mode, no server restart needed
- **Actor model** — Pekko actors provide lightweight concurrent entities that communicate via messages, avoiding shared mutable state
- **Reactive streams** — Pekko Streams handles async data pipelines with built-in backpressure between frontend and backend
- **Type safety** — Scala 3's type system catches errors at compile time; case classes and sealed traits model the domain precisely
- **Non-blocking I/O** — Play and Pekko are async-first, handling many concurrent connections on few threads

## Project Structure

```
app/
├── controllers/
│   ├── Customer.scala                          # Customer REST endpoints
│   └── ShoppingListController.scala            # Shopping list REST endpoints
├── models/
│   ├── Customer.scala                          # Customer case class + JSON format
│   ├── ShoppingList.scala                      # ShoppingList case class + JSON format
│   ├── ShoppingListItem.scala                  # ShoppingListItem case class + JSON format
│   └── requests/
│       └── ShoppingListCreateRequest.scala     # Create shopping list request DTO
├── services/
│   ├── Customer.scala                          # Customer service trait + in-memory impl
│   └── ShoppingList.scala                      # Shopping list service trait + in-memory impl
└── Module.scala                                # Guice DI bindings
conf/
├── application.conf                            # Play/Pekko config (HOCON)
├── routes                                      # URL routing
└── logback.xml                                 # Logging
test/
├── controllers/
│   ├── CustomerControllerSpec.scala
│   └── ShoppingListControllerSpec.scala
├── models/
│   ├── CustomerModelSpec.scala
│   └── ShoppingListItemModelSpec.scala
└── services/
    ├── CustomerServiceImplSpec.scala
    └── ShoppingListServiceImplSpec.scala
```

## How To Run

```bash
sbt run
```

Server starts on **http://localhost:9000** with auto-reloading enabled.

## API

### Create Customer

```
POST /api/v1/customer
Content-Type: application/json

{"email": "user@example.com"}
```

| Status | Response |
|--------|----------|
| 201 | `{"email": "user@example.com"}` |
| 400 | `{"error": "Email is required"}` |
| 400 | `{"error": "Customer with email ... already exists."}` |

### Get Customer by Email

```
GET /api/v1/customer/:email
```

| Status | Response |
|--------|----------|
| 200 | `{"email": "user@example.com"}` |
| 404 | `{"error": "Customer with email ... not found."}` |

### Create Shopping List

```
POST /api/v1/shopping-list
Content-Type: application/json

{
  "email": "user@example.com",
  "name": "Weekly Groceries",
  "items": [
    {"name": "Milk", "quantity": 2},
    {"name": "Bread", "quantity": 1}
  ]
}
```

| Status | Response |
|--------|----------|
| 201 | `{"name": "Weekly Groceries", "items": [{"name": "Milk", "quantity": 2}, {"name": "Bread", "quantity": 1}]}` |
| 400 | `{"error": "Invalid request format", "details": {...}}` |
| 409 | `{"error": "Shopping list already exists for email ..."}` |

### Get Shopping List

```
GET /api/v1/shopping-list/:email
```

| Status | Response |
|--------|----------|
| 200 | `{"name": "Weekly Groceries", "items": [{"name": "Milk", "quantity": 2}, {"name": "Bread", "quantity": 1}]}` |
| 404 | `{"error": "No shopping list found for email ..."}` |

### Examples

```bash
# Create a customer
curl -X POST http://localhost:9000/api/v1/customer \
  -H "Content-Type: application/json" \
  -d '{"email":"hello@example.com"}'

# Get customer by email
curl http://localhost:9000/api/v1/customer/hello@example.com

# Create a shopping list
curl -X POST http://localhost:9000/api/v1/shopping-list \
  -H "Content-Type: application/json" \
  -d '{"email":"hello@example.com","name":"Weekly Groceries","items":[{"name":"Milk","quantity":2},{"name":"Bread","quantity":1}]}'

# Get shopping list for a customer
curl http://localhost:9000/api/v1/shopping-list/hello@example.com
```

## How To Test

### Unit tests

```bash
sbt test
```

### Manual testing

Start the server:

```bash
sbt run
```

Then use curl against **http://localhost:9000** as shown in the examples above.

## Project Status

🚧 **Work in progress** — next steps:

- Add/remove items from existing shopping lists
- Persistent database (H2 → PostgreSQL)
- Pekko actors for concurrent state management
- Frontend integration with Pekko Streams
