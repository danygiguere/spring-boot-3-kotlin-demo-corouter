# Spring Boot 3 Kotlin Demo — CoRouter

A demo project showcasing modern Spring Boot 3 development with Kotlin using the **functional routing (coRouter DSL)** style, built on a fully reactive stack.

## Features

- **CoRouter (Functional Routing)** — Routes defined via Spring's `coRouter` DSL with separate Router and Handler classes, providing a clean separation of routing and request-handling logic
 using `CoroutineCrudRepository` for non-blocking persistence
- **JOOQ (Optional)** — Type-safe SQL queries, complex joins, and eager-loaded nested results via `MULTISET` alongside R2DBC
- **Global Exception Handler** — Centralized error handling with structured, consistent API error responses
- **Swagger / OpenAPI** — Auto-generated interactive API documentation via Springdoc
- **Hibernate Validation (i18n)** — Bean validation with localized constraint violation messages
- **Spotless & Linting** — Automated code formatting and style enforcement
- **Testcontainers** — Integration tests using real database containers for reliable, environment-independent testing

## Tech Stack

- Kotlin · Spring Boot 3 · WebFlux · R2DBC · PostgreSQL · JOOQ · Flyway

## Getting Started

1. Ensure a local PostgreSQL instance is running with a database named `demo_spring_kotlin`
   > 💡 Need a quick local database? Use [github.com/danygiguere/docker_db](https://github.com/danygiguere/docker_db) to spin up a dockerized PostgreSQL instance.
2. Copy `src/main/resources/application.example.yml` and rename the copy to `application.yml` in the same directory, then update it with your local settings
3. Run the application — Flyway will automatically apply migrations on startup
4. Access the Swagger UI at **http://localhost:8080/swagger-ui.html** (port configured in `src/main/resources/application.yml`, default: `8080`)

## Data Model

```
┌─────────────────┐        ┌─────────────────┐
│   Enterprise    │ 1    N │      Team       │
│─────────────────│───────►│─────────────────│
│ id              │        │ id              │
│ name            │        │ enterprise_id   │
│ phoneNumber     │        │ name            │
│ website         │        │ description     │
│ email           │        └────────┬────────┘
│ description     │                 │
└─────────────────┘                 │ N
                                    │
                          ┌─────────┴────────┐
                          │   team_members   │
                          │──────────────────│
                          │ team_id          │
                          │ user_id          │
                          └─────────┬────────┘
                                    │ N
                                    ▼
                           ┌─────────────────┐
                           │      User       │
                           │─────────────────│
                           │ id              │
                           │ name            │
                           │ email           │
                           │ phoneNumber     │
                           └─────────────────┘
```

- **Enterprise → Team**: One-to-Many (a team belongs to one enterprise)
- **Team ↔ User**: Many-to-Many via `team_members` join table

## Testing the API

A Postman collection (`postman_collection.json`) is included at the root of the project — import it to get all endpoints ready to use.

> 🌐 **i18n:** Add the `Accept-Language: fr` header to any request to receive validation error messages in French. Omit it (or use `Accept-Language: en`) for English.

## Commands

| Command | Description |
|---|---|
| `./gradlew clean build` | Format code with Spotless, compile, test and package |
| `./gradlew bootRun` | Start the application |
| `./gradlew test` | Run tests |
| `./gradlew spotlessApply` | Auto-format all source files |
| `./gradlew spotlessCheck` | Check formatting without modifying files (CI) |



