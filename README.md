# SmecklesWebApp

A personal budgeting companion that grows with you — from simple shopping lists to full expense tracking.

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
  - [Why Apache Pekko over Akka?](#why-apache-pekko-over-akka)
  - [Key Features of the Stack](#key-features-of-the-stack)
- [Project Structure](#project-structure)
- [Service Flow](#service-flow)
- [How To Run](#how-to-run)
- [Authentication](#authentication)
  - [How It Works](#how-it-works)
  - [Getting a Bearer Token](#getting-a-bearer-token)
- [API](#api)
  - [Health Check](#health-check)
  - [Create Customer](#create-customer)
  - [Get Customer by Email](#get-customer-by-email)
  - [Create Shopping List](#create-shopping-list)
  - [Get Shopping Lists](#get-shopping-lists)
  - [Examples](#examples)
- [Database Configuration](#database-configuration)
  - [Per-Environment Configuration](#per-environment-configuration)
  - [Current Setup (Local Development + Functional Tests)](#current-setup-local-development--functional-tests)
  - [Unit Test Configuration](#unit-test-configuration)
  - [Switching to PostgreSQL (Production)](#switching-to-postgresql-production)
- [How To Test](#how-to-test)
  - [Unit tests](#unit-tests)
  - [Functional tests](#functional-tests)
  - [Manual testing](#manual-testing)
- [Project Status](#project-status)

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
│   ├── CustomerController.scala                # Customer REST endpoints
│   └── ShoppingListController.scala            # Shopping list REST endpoints
├── models/
│   ├── Customer.scala                          # Customer case class + JSON format
│   ├── ShoppingList.scala                      # ShoppingListWithItems domain model
│   ├── ShoppingListItem.scala                  # ShoppingListItem + DecoupledShoppingListItem
│   └── requests/
│       └── ShoppingListCreateRequest.scala     # Create shopping list request DTO
├── repositories/
│   ├── DataRepository.scala                    # Base trait: async CRUD contract
│   ├── SlickDataRepository.scala               # Base Slick repository (play-slick)
│   ├── InMemoryDataRepository.scala            # In-memory HashMap-backed trait
│   ├── customer/
│   │   ├── CustomerRepository.scala            # Concrete in-memory customer repo
│   │   └── SlickCustomerRepository.scala       # Slick/H2/PostgreSQL customer repo
│   └── shoppinglist/
│       ├── ShoppingListRepository.scala        # Concrete in-memory shopping list repo
│       └── SlickShoppingListRepository.scala   # Slick/H2/PostgreSQL shopping list repo
├── services/
│   ├── Customer.scala                          # Customer service trait + impl
│   └── ShoppingList.scala                      # Shopping list service trait + impl
└── Module.scala                                # Guice DI bindings
conf/
├── application.conf                            # Base config: named H2 (local dev + functional tests)
├── test.conf                                   # Unit test overrides: anonymous H2 (isolated per test)
├── evolutions/
│   └── default/
│       └── 1.sql                               # Initial schema (customers, lists, items)
├── routes                                      # URL routing
└── logback.xml                                 # Logging
test/
├── controllers/
│   ├── CustomerControllerSpec.scala
│   └── ShoppingListControllerSpec.scala
├── models/
│   ├── CustomerModelSpec.scala
│   └── ShoppingListItemModelSpec.scala
├── repositories/
│   ├── customer/
│   │   ├── CustomerRepositorySpec.scala
│   │   └── SlickCustomerRepositorySpec.scala
│   └── shoppinglist/
│       ├── ShoppingListRepositorySpec.scala
│       └── SlickShoppingListRepositorySpec.scala
└── services/
    ├── CustomerServiceImplSpec.scala
    └── ShoppingListServiceImplSpec.scala
functional-tests/
└── api/
    ├── CustomerServiceFunctionalTest.scala     # Customer API end-to-end tests
    └── ShoppingListFunctionalTest.scala        # Shopping list API end-to-end tests
```

## Service Flow

Requests flow through three layers, each with a single responsibility:

```
HTTP Request
     │
     ▼
┌─────────────────────────────────────────────────────────┐
│  Routes (conf/routes)                                   │
│  Maps HTTP method + path → controller action            │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│  Controller (e.g. CustomerController)                   │
│  • Parses/validates the request                         │
│  • Calls the service layer                              │
│  • Maps Future[Either[Error, Entity]] → HTTP Result     │
│  • Returns Future[Result] to Play (non-blocking)        │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│  Service (e.g. CustomerServiceImpl)                     │
│  • Business logic and orchestration                     │
│  • Delegates persistence to the repository              │
│  • Returns Future[Either[String, Entity]]               │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│  Repository (DataRepository trait)                      │
│  ┌───────────────────┐  ┌────────────────────────────┐  │
│  │ InMemoryDataRepo  │  │ SlickRepo (future: H2 /    │  │
│  │ (HashMap, dev)    │  │ PostgreSQL via play-slick)  │  │
│  └───────────────────┘  └────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

**Key design decisions:**

- **Async everywhere** — every layer returns `Future`, so no thread blocks waiting for I/O. Play writes the HTTP response only when the Future completes.
- **Either for errors** — domain errors (not found, already exists) are encoded as `Left(message)` rather than thrown exceptions, making error paths explicit and composable.
- **Swappable repositories** — the `DataRepository` trait defines the contract; Guice bindings in `Module.scala` select the concrete implementation (in-memory for dev/test, database-backed for production).

## How To Run

```bash
sbt run
```

Server starts on **http://localhost:9000** with auto-reloading enabled.

## Authentication

All API endpoints except `/health` require a valid Firebase Auth ID token in the `Authorization` header.

### How It Works

1. User signs in via Google on the frontend (Firebase Auth)
2. Frontend obtains an ID token: `await user.getIdToken()`
3. Frontend sends the token with every request: `Authorization: Bearer <token>`
4. Backend verifies the token signature against Google's public keys (JWKS)
5. Backend checks issuer, audience, and expiry
6. If valid → request proceeds; if invalid → 401 Unauthorized

The backend uses [auth0/java-jwt](https://github.com/auth0/java-jwt) + [auth0/jwks-rsa](https://github.com/auth0/jwks-rsa-java) for verification. No Firebase Admin SDK required.

### Getting a Bearer Token

**Option 1: From the frontend (normal flow)**

```javascript
import { getAuth } from "firebase/auth";
const token = await getAuth().currentUser.getIdToken();
console.log(token); // copy this for curl testing
```

**Option 2: Using the Firebase Auth REST API (for CLI/curl testing)**

```bash
# Sign in with email/password and get an ID token
# Replace API_KEY with your Firebase project's Web API Key
# (Firebase Console → Project Settings → General → Web API Key)
curl -s -X POST \
  "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"your-password","returnSecureToken":true}' \
  | jq -r '.idToken'
```

**Option 3: Using gcloud (if you have Identity Platform enabled)**

```bash
# Prints a valid ID token for the currently authenticated gcloud user
gcloud auth print-identity-token
```

> **Note:** `gcloud auth print-identity-token` produces a Google ID token, not a Firebase ID token. This only works if your project uses Firebase Auth backed by Google Identity Platform and the token audience matches your project ID. For most cases, Option 1 or 2 is simpler.

**Store the token for reuse:**

```bash
export TOKEN="eyJhbGciOi..."

# Then use in requests:
curl -H "Authorization: Bearer $TOKEN" http://localhost:9000/api/v1/customers/me@example.com
```

## API

> **All endpoints except `/health` require authentication.** Include `Authorization: Bearer <token>` in every request. See [Authentication](#authentication) for how to obtain a token.

### Health Check

```
GET /health
```

No authentication required.

| Status | Response |
|--------|----------|
| 200 | `{"status": "ok"}` |

### Create Customer

```
POST /api/v1/customers
Content-Type: application/json

{"email": "user@example.com"}
```

| Status | Response |
|--------|----------|
| 201 | `{"email": "user@example.com"}` |
| 400 | `{"error": "Email is required"}` — missing, null, or empty string |
| 409 | `{"error": "Customer with email ... already exists."}` |

### Get Customer by Email

```
GET /api/v1/customers/:email
```

| Status | Response |
|--------|----------|
| 200 | `{"email": "user@example.com"}` |
| 404 | `{"error": "Customer with email ... not found."}` |

### Create Shopping List

```
POST /api/v1/customers/:email/shopping-lists
Content-Type: application/json

{
  "name": "Weekly Groceries",
  "items": [
    {"name": "Milk", "quantity": 2},
    {"name": "Bread", "quantity": 1}
  ]
}
```

Validation rules:
- `name` — required, cannot be empty
- `items` — required, must contain at least one item
- Each item `name` — required, cannot be empty
- Each item `quantity` — required, must be at least 1

| Status | Response |
|--------|----------|
| 201 | `{"email": "user@example.com", "name": "Weekly Groceries", "items": [{"name": "Milk", "quantity": 2}, {"name": "Bread", "quantity": 1}]}` |
| 400 | `{"error": "Invalid request format", "details": {...}}` — validation failure with field-level errors |
| 409 | `{"error": "Shopping list already exists for email ..."}` |

### Get Shopping Lists

```
GET /api/v1/customers/:email/shopping-lists
```

| Status | Response |
|--------|----------|
| 200 | `[{"email": "user@example.com", "name": "Weekly Groceries", "items": [{"name": "Milk", "quantity": 2}, {"name": "Bread", "quantity": 1}]}]` |
| 500 | `{"error": "..."}` — unexpected server error |

### Examples

```bash
# Create a customer
curl -X POST http://localhost:9000/api/v1/customers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"hello@example.com"}'

# Get customer by email
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:9000/api/v1/customers/hello@example.com

# Create a shopping list
curl -X POST http://localhost:9000/api/v1/customers/hello@example.com/shopping-lists \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Weekly Groceries","items":[{"name":"Milk","quantity":2},{"name":"Bread","quantity":1}]}'

# Get all shopping lists for a customer
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:9000/api/v1/customers/hello@example.com/shopping-lists
```

## Database Configuration

The application uses [Play Evolutions](https://www.playframework.com/documentation/3.0.x/Evolutions) for schema management and [play-slick](https://www.playframework.com/documentation/3.0.x/PlaySlick) for database access. Schema migrations live in `conf/evolutions/default/`.

### Per-Environment Configuration

| File | Purpose | Database | How to activate |
|------|---------|----------|-----------------|
| `conf/application.conf` | Base config (local dev + functional tests) | Named H2 in-memory (`mem:shoppinglist`) with `DB_CLOSE_DELAY=-1` | Always loaded by default |
| `conf/test.conf` | Unit test overrides | Anonymous H2 in-memory (`mem:`) — isolated per test, no shared state | `sbt test` (forked JVM via `build.sbt`) |
| `conf/production.conf` | Production overrides (planned) | External PostgreSQL | `-Dconfig.resource=production.conf` |

### Current Setup (Local Development + Functional Tests)

`conf/application.conf` — Named H2 in-memory database running in **PostgreSQL compatibility mode**:

```hocon
slick.dbs.default {
  profile = "slick.jdbc.H2Profile$"
  db {
    driver = "org.h2.Driver"
    url = "jdbc:h2:mem:shoppinglist;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=true"
    user = "sa"
    password = ""
  }
}
```

- `mem:shoppinglist` — **named** in-memory DB; all repositories in the same JVM share this single database instance (customers + shopping lists coexist)
- `DB_CLOSE_DELAY=-1` — keeps the DB alive for the lifetime of the JVM even when no connections are open
- `MODE=PostgreSQL` — ensures SQL syntax compatibility so the same evolutions and queries work against both H2 and PostgreSQL
- `DATABASE_TO_LOWER=true` — forces lowercase table names to match PostgreSQL behaviour
- `play.evolutions.db.default.autoApply = true` — applies pending migrations automatically on startup

### Unit Test Configuration

`conf/test.conf` — Anonymous H2 for **complete test isolation**:

```hocon
include "application.conf"

slick.dbs.default.db.url = "jdbc:h2:mem:;MODE=PostgreSQL;DATABASE_TO_LOWER=true"
```

- No database name after `mem:` — each new connection pool gets its own **private** database instance
- No `DB_CLOSE_DELAY` — DB dies when the connection pool closes
- Combined with `GuiceOneAppPerTest`, each test gets a fresh empty database with no leftover state from other tests

Applied automatically via `build.sbt`:

```scala
Test / javaOptions += "-Dconfig.resource=test.conf"
Test / fork := true
```

### Switching to PostgreSQL (Production)

Planned `conf/production.conf`:

```hocon
include "application.conf"

slick.dbs.default {
  profile = "slick.jdbc.PostgresProfile$"
  db {
    driver = "org.postgresql.Driver"
    url = ${DB_URL}
    user = ${DB_USERNAME}
    password = ${DB_PASSWORD}
  }
}

play.evolutions.db.default.autoApply = false
```

Run with: `sbt "run -Dconfig.resource=production.conf"` or set `JAVA_OPTS=-Dconfig.resource=production.conf`.

The PostgreSQL driver dependency is already included in `build.sbt`.

## How To Test

### Unit tests

```bash
sbt test
```

### Functional tests

End-to-end tests that boot the full application and exercise the real router, DI, and controllers:

```bash
sbt functional:test
```

### Manual testing

Start the server:

```bash
sbt run
```

Get a token (see [Getting a Bearer Token](#getting-a-bearer-token)), then use curl against **http://localhost:9000**:

```bash
export TOKEN="<your-firebase-id-token>"
curl -H "Authorization: Bearer $TOKEN" http://localhost:9000/api/v1/customers/me@example.com
```

## Project Status

🚧 **Work in progress** — next steps:

- ~~Integrate Slick repositories with customer/shopping list services (replace in-memory repos)~~ ✅
- ~~Get shopping lists returns list response (supports multiple lists per customer)~~ ✅
- Remove customer email from shopping list responses (redundant with path parameter)
- Add/remove items from existing shopping lists
- Persistent database (H2 → PostgreSQL)
- Pekko actors for concurrent state management
- Frontend integration with Pekko Streams

### Planned RESTful API Improvements

#### Current API (v0.1) — Deprecated

| Method | Endpoint | Issue |
|--------|----------|-------|
| `POST` | `/api/v1/shopping-list` | Top-level resource implies shopping lists exist independently; email buried in request body |
| `GET` | `/api/v1/shopping-list/:email` | Flat structure can't distinguish "get all lists" from "get one list"; no path to CRUD by name |

#### Planned API

Shopping lists are always scoped under customers — they don't exist as an independent resource:

```
POST   /api/v1/customers                                   # create customer ✅
GET    /api/v1/customers/:email                            # get customer ✅

POST   /api/v1/customers/:email/shopping-lists             # create a list ✅
GET    /api/v1/customers/:email/shopping-lists             # get all lists for customer ✅
GET    /api/v1/customers/:email/shopping-lists/:name       # get one list by name
PUT    /api/v1/customers/:email/shopping-lists/:name       # update a list
DELETE /api/v1/customers/:email/shopping-lists/:name       # delete a list
```

**Why this is better:**

- **Hierarchy is explicit** — the URL path makes ownership clear (list belongs to customer)
- **No top-level orphan** — shopping lists don't exist outside the context of a customer
- **Supports many lists per customer** — the collection endpoint returns all lists; individual lists are addressed by name
- **Composite key in the URL** — `email + name` uniquely identifies a list without exposing internal IDs
- **Standard REST semantics** — plural nouns, collection vs item distinction, HTTP verbs map directly to CRUD operations
- **Email moves out of the request body** — the parent resource path provides context; the body only contains the list payload
