# AGENTS.md

Guidance for AI agents working in this repository. Read this before making changes.

## Project

Spring Boot 3 + Kotlin demo built on a **fully reactive** stack, using the **functional routing (`coRouter` DSL)** style with separate Router and Handler classes.

**Stack:** Kotlin 2.1 · Spring Boot 3.4 · WebFlux · R2DBC · PostgreSQL · Flyway · Springdoc/OpenAPI · Testcontainers · JVM 21

**Data model:** `Enterprise` 1—N `Team`, `Team` N—N `User` via the `team_members` join table.

## Commands

| Command | Description |
|---|---|
| `./gradlew clean build` | Spotless check, compile, test, package (full CI gate) |
| `./gradlew bootRun` | Start the application |
| `./gradlew test` | Run tests |
| `./gradlew spotlessApply` | Auto-format all sources |
| `./gradlew spotlessCheck` | Verify formatting (runs before `compileKotlin`) |

`compileKotlin` depends on `spotlessCheck` — **a formatting violation fails the build.** Run `./gradlew spotlessApply build` to auto-fix and build. Spotless uses `ktlint 1.5.0`.

## Agent Behaviour Rules

- **Be ultra concise.** In code, comments, documentation, and responses — say as little as possible to convey the point.
- **Never commit on behalf of the user.** Do not run `git commit` unless explicitly asked.
- **Write documentation and comments as correct content directly.** Do not add text explaining why something changed; the content stands on its own.
- **Match the surrounding code** — naming, idioms, comment density, formatting.

## Architecture

Request flow, one layer per responsibility:

```
Router (coRouter DSL + OpenAPI)  →  Handler (HTTP I/O, validation)  →  Service (business logic)  →  Repository (R2DBC)
```

- **Router** (`router/`) — `@Configuration` exposing a `coRouter { }` `@Bean`. Routes use `.nest("/x")`. Annotate with `@RouterOperations`/`@RouterOperation` so Springdoc generates OpenAPI docs. **Order routes carefully:** literal/longer paths before catch-alls (e.g. `GET("/{userId}/teams")` before `GET("/{id}")`).
- **Handler** (`handler/`) — `@Component`. Reads `ServerRequest`, validates the body, calls the service, builds `ServerResponse`. Keep business logic out. Use `awaitBody<T>()`, `bodyValueAndAwait(x)` (single), `bodyAndAwait(flow)` (stream).
- **Service** (`service/`) — `@Service`. Business logic and orchestration. Returns entities/DTOs or `Flow<T>`; throws `AppException.*`.
- **Repository** (`repository/`) — `CoroutineCrudRepository<Entity, Long>`. Derived queries return `Flow<T>` for collections, suspend for single rows.
- **Entity** (`domain/entity/`) — `data class` with `@Table` and nullable `@Id val id: Long? = null`.
- **DTO** (`dto/`) — request (validated input) and response/projection types.

## Key Patterns

### Reactive — never block
All APIs are non-blocking (coroutines over Reactor). R2DBC repositories and async clients are already non-blocking.

**Any blocking/synchronous call (blocking Java SDK, JDBC, etc.) inside a `suspend fun` must be wrapped in `withContext(Dispatchers.IO)`.** Coroutines do not auto-detect blocking; an unwrapped blocking call stalls a WebFlux event-loop thread and degrades the whole server under load.

```kotlin
// ❌ blocks the event loop
suspend fun load(id: String) = SomeSdk.retrieve(id)

// ✅ runs on the IO pool
suspend fun load(id: String) = withContext(Dispatchers.IO) { SomeSdk.retrieve(id) }
```

> `withContext(Dispatchers.IO)` is a thread switch, **not** a transaction.

### N+1 queries ⚠️
**Never issue a DB call per item in a collection.** The pattern hides across method/layer boundaries — an outer query feeding a per-row inner query. Batch-fetch with an `…In(ids)` query, then join in memory.

The canonical correct shape lives in `EnterpriseService.searchByName`: fetch enterprises → `findAllByEnterpriseIdIn` → `findAllByTeamIdIn`, each `groupBy` the FK, assembled in memory. Three queries regardless of result size — not one-per-row.

```kotlin
// ❌ N+1 — 1 query for teams, then 1 per team
teams.map { team -> memberRepo.findAllByTeamId(team.id).toList() }

// ✅ 1 batch query, grouped in memory
val byTeam = memberRepo.findAllByTeamIdIn(teams.mapNotNull { it.id }).toList().groupBy { it.teamId }
teams.map { team -> byTeam[team.id].orEmpty() }
```

### Null handling
Prefer a safe call over a redundant null-check-then-deref: `user?.email != null`, not `user != null && user.email != null`.

**Positive checks only.** Keep the expanded form for `== null` and negated comparisons — collapsing flips the result when the receiver is null (e.g. `team != null && team.name != EXPECTED` must stay expanded, or the body NPEs). Review convention; no ktlint rule enforces it.

### Validation & i18n
- Request DTOs carry Jakarta Bean Validation annotations with i18n message keys: `@field:NotBlank(message = "{user.name.required}")`.
- Handlers validate via `validator.validateAndThrow(request.awaitBody<T>(), request.locale())` (see `extension/ValidatorExtensions.kt`). This sets the locale, validates, and throws `ConstraintViolationException` on failure.
- Messages live in `messages.properties` (English) and `messages_fr.properties` (French). Add every new key to **both**.
- Clients select language with the `Accept-Language: fr` header; default is English.

### Error handling
Throw `AppException.*` (`exception/AppException.kt`) — i18n-aware, user-safe message keys resolved by the global `ApplicationExceptionHandler`:

| Exception | Status |
|---|---|
| `AppException.BadRequest(key, args)` | 400 |
| `AppException.Unauthorized(key, args)` | 401 |
| `AppException.Forbidden(key, args)` | 403 |
| `AppException.NotFound(key, args)` | 404 |
| `AppException.Conflict(key, args)` | 409 |
| `AppException.ValidationErrors(fieldErrors, summaryKey)` | 422 |

- `key` is an i18n message key; `args` fills `{0}`, `{1}` placeholders (e.g. `AppException.NotFound("error.user.not.found", arrayOf(id))`).
- Use `ValidationErrors` for multi-field business rules that Bean Validation annotations can't express (cross-field, DB-uniqueness).
- `ConstraintViolationException` / `WebExchangeBindException` → 422 with per-field errors; `AccessDeniedException`, `ServerWebInputException`, `ResponseStatusException` are handled and their internals suppressed. **Don't** build ad-hoc error responses — extend the handler instead.
- All errors return `ApiErrorResponse(message, code, correlationId, errors?)`. Framework/5xx messages are generic to avoid leaking internals; a `correlationId` is logged with every failure.

### Atomicity & transactions ⚠️
**Any service method doing 2+ DB mutations must be atomic.** A partial write (delete-then-failed-save, parent-saved-but-children-missing) is a bug.

- **Default:** `@Transactional` (`org.springframework.transaction.annotation`) on the **public** service method. It's proxy-based, so it is **silently ignored** on `private` methods and on **self-invocation** (a method calling another in the same class). Put the boundary on the public entry point.
- **For self-invoked or sub-block scopes**, use programmatic `TransactionalOperator`:
  ```kotlin
  class FooService(/* … */, transactionManager: ReactiveTransactionManager) {
      private val tx = TransactionalOperator.create(transactionManager)
      suspend fun bar() = tx.executeAndAwait { /* DB writes */ }
  }
  ```
- Reactive tx context propagates through the coroutine context, so `@Transactional` survives a `withContext`, but the switch itself guarantees nothing.
- **Keep external calls (HTTP, email, object storage, payment SDKs) OUTSIDE the transaction** — a DB tx can't roll them back and they shouldn't hold a connection. Do reads first, open the tx for DB writes only, run side effects after commit. Make external mutations **idempotent** (deterministic keys, persisted external ids) so a crash between the call and the commit reconciles on retry instead of duplicating.

### Security — request DTOs are the allow-list ⚠️
A client can put any value in a request body. **Accept only fields it's legitimately allowed to set; derive or authoritatively fetch everything else server-side.** If a field would let the client influence a server-owned value, it doesn't belong in the request DTO.

Never accept from the client — set server-side: `id`, ownership/identity (`userId`, `ownerId`), state/role/privilege flags, timestamps (`createdAt`/`updatedAt`), and computed values. Current request DTOs (`UserRequest`, `TeamRequest`, `EnterpriseRequest`) correctly exclude `id` — entities default `@Id` to `null` and the DB assigns it.

> When authentication is added: any handler taking a resource id must verify the resource belongs to the authenticated principal (existence ≠ authorization — ids are sequential and enumerable). Derive the principal from the auth context, never a client-supplied id; throw `AppException.Forbidden` on mismatch (or `NotFound` when existence itself is sensitive).

## Database & migrations

All schema changes go through **Flyway** migrations in `src/main/resources/db/migration/` — never alter the DB directly. Flyway applies migrations on startup.

- **Before adding a migration:** check the latest version and increment. Current latest is `V1__init.sql`; the next is `V2__<description>.sql`.
- **Primary keys:** `id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`, always first.
- **Foreign keys:** named constraint (`fk_<table>_<ref>`); index the FK column when it drives lookups.
- **`ON DELETE CASCADE`:** only on join/association tables, where a row has no meaning without its parent (e.g. `team_members` → `teams`/`users`). Keep the default RESTRICT for parents that own substantial entities (e.g. `teams` → `enterprises`) so a single delete can't silently wipe a subtree.
- **Enums:** `VARCHAR(N) NOT NULL DEFAULT 'X' CHECK (col IN (...))` — no PostgreSQL `ENUM` type.
- Prefer `NOT NULL` + `DEFAULT`; allow nullable only when absence is meaningful.
- `COMMENT ON COLUMN` for any non-obvious column.
- **Soft delete** (if introduced): nullable `deleted_at TIMESTAMP` as the last column, with a partial index `WHERE deleted_at IS NOT NULL` and a comment.
- Keep R2DBC entity field order aligned with the table; map snake_case columns to camelCase properties.

## Testing

- Integration tests extend `SharedTestContainers` (`src/test/.../shared/config/`), which starts **one reusable PostgreSQL Testcontainer** for the whole suite and wires R2DBC + Flyway via `@DynamicPropertySource`.
- Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with an autowired `WebTestClient` to exercise full routes (see `controller/UserControllerTest`).
- Assert HTTP status and JSON body (`jsonPath`); cover the success path and validation/error paths (e.g. 422 with `$.errors.<field>`).
- Router-level OpenAPI/Swagger wiring is covered by the `*SwaggerTest` classes in `router/`.
- Test names use backtick descriptions: `` `create user - returns 422 when required fields are blank` ``.
- Docker must be available to run the suite.
