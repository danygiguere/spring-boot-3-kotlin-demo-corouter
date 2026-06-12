# Spring Boot 3 Kotlin Demo — CoRouter

A demo project showcasing modern Spring Boot 3 development with Kotlin using the **functional routing (coRouter DSL)** style, built on a fully reactive stack.

## Features

- **CoRouter (Functional Routing)** — Routes defined via Spring's `coRouter` DSL with separate Router and Handler classes, providing a clean separation of routing and request-handling logic
 using `CoroutineCrudRepository` for non-blocking persistence
- **Global Exception Handler** — Centralized error handling returning RFC 9457 problem details (`application/problem+json`); only `AppException` messages reach the client, everything else fails closed
- **Swagger / OpenAPI** — Auto-generated interactive API documentation via Springdoc
- **Hibernate Validation (i18n)** — Bean validation with localized constraint violation messages
- **Spotless & Linting** — Automated code formatting and style enforcement
- **Testcontainers** — Integration tests using real database containers for reliable, environment-independent testing

## Tech Stack

- Kotlin · Spring Boot 3 · WebFlux · R2DBC · PostgreSQL · Flyway

## Environment Variables

Local development uses `.env` support through `spring-dotenv`.

Create a `.env` file from `.env.example` and provide:

- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `DB_PORT`
- `SERVER_PORT`

## Getting Started

1. Ensure a local PostgreSQL instance is running
   > 💡 Need a quick local database? Use [github.com/danygiguere/docker_db](https://github.com/danygiguere/docker_db) to spin up a dockerized PostgreSQL instance.
2. Copy `.env.example` to `.env`, then update it with your local PostgreSQL settings
3. Run the application — Flyway will automatically apply migrations on startup
4. Access the Swagger UI at **http://localhost:8080/swagger-ui.html** by default, or use the `SERVER_PORT` value from your `.env`

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
| `./gradlew clean build` | Check formatting with Spotless, compile, test and package |
| `./gradlew bootRun` | Start the application |
| `./gradlew test` | Run tests |
| `./gradlew spotlessApply` | Auto-format all source files |
| `./gradlew spotlessCheck` | Check formatting without modifying files (CI) |


