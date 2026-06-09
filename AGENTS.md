# AGENTS.md

Guidance for AI agents working in this repository. Read this before making changes.

⚠️ marks invariants whose violation is a bug or vulnerability. Each has an on-demand audit skill — see [Skills (audits)](#skills-audits).

## Contents

- [Project](#project)
- [Commands](#commands)
- [Agent behaviour rules](#agent-behaviour-rules)
- [Architecture](#architecture)
- [Conventions](#conventions)
  - [Reactive — never block ⚠️](#reactive--never-block-)
  - [Reactive — no fire-and-forget ⚠️](#reactive--no-fire-and-forget-)
  - [N+1 queries ⚠️](#n1-queries-)
  - [Atomicity & transactions ⚠️](#atomicity--transactions-)
  - [Error handling](#error-handling)
  - [Mass assignment — never trust client-controlled input ⚠️](#mass-assignment--never-trust-client-controlled-input-)
  - [Security: IDOR ⚠️](#security-idor-)
  - [Response shape ⚠️](#response-shape-)
  - [Observability — logging](#observability--logging)
  - [Stateless architecture ⚠️](#stateless-architecture-)
  - [Null handling](#null-handling)
  - [Validation & i18n](#validation--i18n)
- [Database & migrations](#database--migrations)
- [Testing](#testing)
- [Skills (audits)](#skills-audits)

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

## Agent behaviour rules

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
- **DTO** (`dto/`) — request types (validated input) and projection types for composite/joined data (`EnterpriseWithTeams`, `TeamWithMembers`, `TeamSummary`). Simple resources are returned as their entity directly. No response envelope — see [Response shape](#response-shape-).

## Conventions

### Reactive — never block ⚠️

All APIs are non-blocking (coroutines over Reactor). R2DBC repositories and async clients are already non-blocking.

**Any blocking/synchronous call (blocking Java SDK, JDBC, etc.) inside a `suspend fun` must be wrapped in `withContext(Dispatchers.IO)`.** Coroutines do not auto-detect blocking; an unwrapped blocking call stalls a WebFlux event-loop thread and degrades the whole server under load.

```kotlin
// ❌ blocks the event loop
suspend fun load(id: String) = SomeSdk.retrieve(id)

// ✅ runs on the IO pool
suspend fun load(id: String) = withContext(Dispatchers.IO) { SomeSdk.retrieve(id) }
```

> `withContext(Dispatchers.IO)` is a thread switch, **not** a transaction.

Audit: `blocking-call-audit`.

### Reactive — no fire-and-forget ⚠️

A cold producer that is never terminated **silently does nothing**:

- a `Flow` (repository/service return) that is never `.collect`/`.toList()`'d — the query never runs;
- a Reactor `Mono`/`Flux` (from interop) created but never `.await*()`'d, returned, or composed.

Terminate every producer: suspend `CoroutineCrudRepository` calls (`save`/`delete`) already execute; for a Mono use `.awaitSingleOrNull()`/`.awaitFirstOrNull()`; for a Flow use `.collect`/`.toList()` or return it in the chain. Avoid bare `.subscribe()` — it swallows errors and drops the reactive context (tx, correlation).

Audit: `fire-and-forget-audit`.

### N+1 queries ⚠️

**Never issue a DB call per item in a collection.** The pattern hides across method/layer boundaries — an outer query feeding a per-row inner query. Batch-fetch with an `…In(ids)` query, then join in memory.

The canonical correct shape lives in `EnterpriseService.searchByName` and `TeamService.findTeamsWithEnterpriseInfo`: fetch the collection → one `findAllByXIn` / `findAllById` per relation → `groupBy`/`associateBy` the FK, assembled in memory. Constant query count regardless of result size — not one-per-row.

```kotlin
// ❌ N+1 — 1 query for users + 1 query per user for posts.
//          25 users → 26 queries, and it grows with the result set.
val users = userRepo.findAll().toList()              // query 1: fetch users
users.map { user ->
    postRepo.findAllByUserId(user.id).toList()       // query 2..N+1: one per user 👈
}

// ✅ 2 queries total, regardless of result size.
val users = userRepo.findAll().toList()              // query 1: fetch users
val userIds = users.mapNotNull { it.id }
val postsByUser =
    postRepo.findAllByUserIdIn(userIds)              // query 2: batch-fetch ALL posts at once
        .toList()
        .groupBy { it.userId }                       // in-memory grouping — no extra query
users.map { user ->
    user.copy(posts = postsByUser[user.id].orEmpty())
}
```

Audit: `nplus1-audit`.

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
- Reactive tx context propagates through the coroutine context, so `@Transactional` survives a `withContext`, but `withContext` itself provides no transactional guarantee — atomicity still requires `@Transactional` or `TransactionalOperator`.
- **Keep external calls (HTTP, email, object storage, payment SDKs) OUTSIDE the transaction** — a DB tx can't roll them back and they shouldn't hold a connection. Do reads first, open the tx for DB writes only, run side effects after commit. Make external mutations **idempotent** (deterministic keys, persisted external ids) so a crash between the call and the commit reconciles on retry instead of duplicating.

Audit: `atomicity-audit`.

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
- Pass the original `cause` when wrapping a caught error: `AppException.Conflict(key, args, e)` (see `UserService.assignToTeam`).
- Use `ValidationErrors` for multi-field business rules that Bean Validation annotations can't express (cross-field, DB-uniqueness).
- `ConstraintViolationException` / `WebExchangeBindException` → 422 with per-field errors; `AccessDeniedException`, `ServerWebInputException`, `ResponseStatusException` are handled and their internals suppressed. **Don't** build ad-hoc error responses — extend the handler instead.
- All errors return an **RFC 9457 problem detail** (`application/problem+json`): `type`, `title`, `status`, `detail` (the resolved message), `instance` (request path), plus extensions `correlationId` and `errors?`. Framework/5xx messages are generic to avoid leaking internals; the `correlationId` is logged with every failure.
- `type` is derived from the message key (`error.username.taken` → `/errors/username-taken`) for `AppException.*` and validation errors; other errors keep `about:blank`. **Message keys are therefore part of the API contract — renaming one changes its `type` URI.**

Audit: `exception-audit`.

### Mass assignment — never trust client-controlled input ⚠️

A client can put any value in a request body. **Accept only fields it's legitimately allowed to set; derive or authoritatively fetch everything else server-side.** If a field would let the client influence a server-owned value, it doesn't belong in the request DTO.

Never accept from the client — set server-side: `id`, state/role/privilege flags (`role`, `status`, `verified`, …), timestamps (`createdAt`/`updatedAt`), and computed values. Current request DTOs (`UserRequest`, `TeamRequest`, `EnterpriseRequest`) correctly exclude `id` — entities default `@Id` to `null` and the DB assigns it.

Audit: `mass-assignment-audit` (privilege/bookkeeping fields); ownership/identity ids → [Security: IDOR](#security-idor-).

### Security: IDOR ⚠️

**Ownership/identity ids** (`userId`, `ownerId`, an `enterpriseId` used for authorization) are the ownership special case of the same never-trust-client-input rule ([Mass assignment](#mass-assignment--never-trust-client-controlled-input-)) — the variant that depends on **who the caller is** — they must come from the authenticated principal, not the payload, and existence of a row must never imply authorization (ids are sequential `BIGINT`, hence enumerable).

> This project has **no authentication yet.** Until a principal exists there is nothing to enforce ownership against. When authentication is added: any handler taking a resource id must verify the resource belongs to the authenticated principal — derive the principal from the auth context, scope the query (`findByIdAndOwnerId…`) or assert ownership, and throw `AppException.Forbidden` on mismatch (or `NotFound` when existence itself is sensitive).

Audit: `idor-audit` (forward-looking until auth lands — inventories the id-taking surface).

### Response shape ⚠️

**Return the resource representation directly and let the HTTP status carry the semantics — no success envelope.** Returning an `@Table` entity directly is fine *when it carries no sensitive data* (`bodyValueAndAwait(user)`); the moment an entity would expose a secret or internal field, introduce a scoped `*Response` DTO (or a projection) instead of the entity.

- **Status carries meaning:** `200` read, `201` created (returns the created resource), `201`/`204` with **no body** for a no-payload action (e.g. `assignToTeam`). Errors go through `ApplicationExceptionHandler` (RFC 9457 problem detail — see [Error handling](#error-handling)).
- **No success envelope** (`DataResponse`/`{data,message}`). The one exception is a *paginated* collection, which may wrap items with page metadata — not yet used here.
- **Never expose secrets/internal fields ⚠️:** credentials/secrets (`passwordHash`, tokens, `*Secret`) or internal bookkeeping (`deletedAt`, audit columns, internal flags) must never reach a serialized response. If an entity gains such a field, stop returning the entity and add a scoped `*Response` DTO. Don't leak another principal's PII (`email`/`phoneNumber`).
- **Composite/joined data** uses a projection DTO (`EnterpriseWithTeams`, `TeamWithMembers`, `TeamSummary`) — there's no single entity to return.

Audit: `response-exposure-audit`.

### Observability — logging

Failures must surface, and logs must not leak data.

- Logging uses `io.github.oshai.kotlinlogging.KotlinLogging` (`private val logger = KotlinLogging.logger {}`). **Never** `println` / `System.out`.
- **Don't swallow exceptions** — a blind `catch { }`, a catch returning `null`/a default without logging, or a reactive `onErrorResume { Mono.empty() }` hides a real failure. Either let it reach `ApplicationExceptionHandler` or log it with its cause.
- The central handler logs 4xx-shaped failures at `warn` and 5xx at `error`, each prefixed with the `correlationId`. Match those levels.
- **Never log secrets or PII** — no tokens/passwords, no `email`/`phoneNumber`, no full request/DTO bodies.

Audit: `observability-audit`.

### Stateless architecture ⚠️

Keep instances stateless so the app can run behind a load balancer (any request may hit any instance). The concrete shared store / scheduler / lock is a deployment choice — this project stays infra-agnostic.

- **No authoritative or consistency-critical state in process** — if losing it on restart, or two instances disagreeing, would be incorrect (not just a cache miss), it belongs in the DB or a shared store. A short-TTL local cache of DB-backed data is fine.
- **`@Scheduled` fires on every instance** — if more than one runs, in-process schedules duplicate work. Drive periodic work from a single external trigger or guard it with a shared lock.

Audit: `stateless-audit`.

### Null handling

Prefer a safe call over a redundant null-check-then-deref: `user?.email != null`, not `user != null && user.email != null`.

**Positive checks only.** Keep the expanded form for `== null` and negated comparisons — collapsing flips the result when the receiver is null (e.g. `team != null && team.name != EXPECTED` must stay expanded, or the body NPEs). Review convention; no ktlint rule enforces it.

### Validation & i18n

- Request DTOs carry Jakarta Bean Validation annotations with i18n message keys: `@field:NotBlank(message = "{user.name.required}")`.
- Handlers validate via `validator.validateAndThrow(request.awaitBody<T>(), request.locale())` (see `extension/ValidatorExtensions.kt`). This sets the locale, validates, and throws `ConstraintViolationException` on failure.
- Messages live in `messages.properties` (English) and `messages_fr.properties` (French). Add every new key to **both**.
- Clients select language with the `Accept-Language: fr` header; default is English.

## Database & migrations

All schema changes go through **Flyway** migrations in `src/main/resources/db/migration/` — never alter the DB directly. Flyway applies migrations on startup.

- **Before adding a migration:** list `src/main/resources/db/migration/` for the highest `V<N>` and name yours `V<N+1>__<description>.sql`.
- **Primary keys:** `id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`, always first.
- **Foreign keys:** named constraint (`fk_<table>_<ref>`); index the FK column when it drives lookups.
- **`ON DELETE CASCADE`:** only on join/association tables, where a row has no meaning without its parent (e.g. `team_members` → `teams`/`users`). Keep the default RESTRICT for parents that own substantial entities (e.g. `teams` → `enterprises`) so a single delete can't silently wipe a subtree.
- **Enums:** `VARCHAR(N) NOT NULL DEFAULT 'X' CHECK (col IN (...))` — no PostgreSQL `ENUM` type.
- Prefer `NOT NULL` + `DEFAULT`; allow nullable only when absence is meaningful.
- `COMMENT ON COLUMN` for any non-obvious column.
- **Soft delete** (if introduced): nullable `deleted_at TIMESTAMP` as the last column, with a partial index `WHERE deleted_at IS NOT NULL` and a comment.
- Map snake_case columns to camelCase properties.

Audit: `migration-safety-audit`.

## Testing

- Integration tests extend `SharedTestContainers` (`src/test/.../shared/config/`), which starts **one reusable PostgreSQL Testcontainer** for the whole suite and wires R2DBC + Flyway via `@DynamicPropertySource`.
- Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with an autowired `WebTestClient` to exercise full routes (see `controller/UserControllerTest`).
- Assert HTTP status and JSON body (`jsonPath`); cover the success path and validation/error paths (e.g. 422 with `$.type`, `$.status`, `$.errors.<field>`).
- Router-level OpenAPI/Swagger wiring is covered by the `*SwaggerTest` classes in `router/`.
- Test names use backtick descriptions: `` `create user - returns 422 when required fields are blank` ``.
- Docker must be available to run the suite.

## Skills (audits)

On-demand audits that verify the ⚠️ rules above live in `.github/skills/<name>/SKILL.md` (Copilot reads them there natively; Claude Code reads them via the `.claude/skills` symlink). They are **not** auto-loaded — invoke the relevant one when reviewing or adding code. `review-conventions` routes a diff to the right subset; `audit` runs a whole bundle (by `tags:`) over a chosen scope (diff, a layer, or the repo). Each skill is tagged `security` / `correctness` / `scaling` / `db` / `meta` — `audit list` prints the live bundle map.

| Rule / area | Skill |
|---|---|
| Reactive — no blocking calls | `blocking-call-audit` |
| Reactive — no fire-and-forget (unawaited publishers) | `fire-and-forget-audit` |
| N+1 queries | `nplus1-audit` |
| Atomicity & transactions | `atomicity-audit` |
| Error handling (`AppException` pattern) | `exception-audit` |
| Mass assignment — never trust client-controlled input (privilege/bookkeeping fields in request DTOs) | `mass-assignment-audit` |
| Security — IDOR (ownership of id-taking endpoints) | `idor-audit` |
| Response shape (no envelope, no secret/PII leak) | `response-exposure-audit` |
| Observability — logging | `observability-audit` |
| Stateless architecture (in-process state, `@Scheduled`) | `stateless-audit` |
| Database & migration safety (per-file `.sql` review) | `migration-safety-audit` |
| General security vuln pass (diff/PR; portable `/security-review`) | `security-audit` |
| Route a diff to the relevant audits | `review-conventions` |
| Run a bundle (`security`/`correctness`/`scaling`/`db`/`all`) over a scope | `audit` |
