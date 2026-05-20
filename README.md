# Shopping List App Service

A Spring Boot REST API for managing shopping lists and items. This is a **work in progress** project.

## Overview

A backend service that provides REST APIs for creating and managing shopping lists. The long-term goal is to pair this server with a lightweight mobile app that allows users to:

- Create and manage multiple shopping lists
- Add items to lists
- Access their shopping lists from anywhere
- Build a personal, independent alternative to existing play store shopping list apps

## Features

✅ **Customer Management (probably to be removed)**
- Create an identity/customer 
- Retrieve customer email (for use in sending reminders to shop in the future)

✅ **Shopping Lists**
- Associate list of items to shop for 
- Can have many different shopping lists
- Can name the list (e.g. groceries, vinted)

✅ **Items**
- Manage items database
- Add new items (maybe with image feature)
- Support for many-to-many relationships

✅ **Database**
- H2 in-memory database for development
- Hibernatejpa configuration with automatic schema creation
- PostgreSQL support (configurable)

## Tech Stack

- **Java 11+**
- **Spring Boot 3.3.0**
- **Spring Data JPA**
- **H2 Database** (development)
- **PostgreSQL** (production ready)
- **Gradle** (build tool)
- **JUnit 5** (testing)

## How To Run

```
./gradlew bootRun --args='--spring.devtools.restart.enabled=true'
```

## How To Use

#### Configuration

All defined in src/main/java/resources

```
# Server Configuration
server.port=8080

# H2 In-Memory Database (auto-creates on startup)
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# JPA/Hibernate Configuration
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=false

# H2 Console (optional - view DB at http://localhost:8080/h2-console)
spring.h2.console.enabled=true
```

- server.port is the port the server runs on
- spring.datasource is the database connection information and driver (h2 currently)
- spring.jpa is the DTO mapping library used
- spring.h2 is h2 specific settings like a console to inspect the data if issues arise


#### Customer API

add new customer 

```
curl -X POST http://localhost:8080/api/v1/customer \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com"}'
```

get customer info

```
curl -X GET http://localhost:8080/api/v1/customer/{id}
```

Example e2e:

```
> curl -X POST http://localhost:8080/api/v1/customer \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com"}'
john@example.com%   
                                                                                                                                                                                             
> curl -X GET http://localhost:8080/api/v1/customer/1 
{"email":"john@example.com"}%     
```

#### Shopping List API

TODO

#### Item API

TODO


