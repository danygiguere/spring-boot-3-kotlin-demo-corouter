---
name: blocking-call-audit
description: Find blocking calls that stall the WebFlux event loop — sync SDK calls in suspend fns without withContext, .block(), Thread.sleep, runBlocking, JDBC. Use when adding an external/HTTP/SDK call or any suspend service method.
tags: [scaling]
---

# Skill: blocking-call-audit

Find blocking calls on WebFlux event-loop threads.

Canonical rules: `AGENTS.md` §"Reactive — never block". This is the on-demand check for that rule (there is no BlockHound test in the build; the rule is enforced by review).

## Rules

- Be ultra concise in all output.

## When to use

Run when adding/reviewing a `suspend fun`, or any call into a synchronous SDK / blocking HTTP client / JDBC.

## Instructions

1. Grep `src/main/kotlin` for outright blocking:
   - `.block()`, `.blockFirst()`, `.blockLast()`, `.toIterable()`, `.toStream()`
   - `Thread.sleep(`, `runBlocking`
   - JDBC / blocking drivers: `DriverManager`, `java.sql.`, `JdbcTemplate`
2. **Sync SDK calls inside a `suspend fun` not wrapped in `withContext(Dispatchers.IO)`** — the main offender. Flag synchronous calls into any third-party / Java SDK or blocking HTTP client (`*.create()/.retrieve()/.list()/.execute()` with no `suspend`/`.await()` equivalent).
3. Confirm what's already safe (don't flag): R2DBC `CoroutineCrudRepository` calls, reactive clients awaited with `.await*()`, `delay(...)` (non-blocking).
4. For each finding, the fix is `withContext(Dispatchers.IO) { <call> }` (or switch to a non-blocking client).

## Output format

```
Blocking-Call Audit
===================
Suspect calls: N

  src/main/kotlin/.../SomeService.kt:51  suspend fun load()
    SomeSdk.retrieve(...) — synchronous SDK call on the event loop, no withContext
    Fix: withContext(Dispatchers.IO) { SomeSdk.retrieve(...) }

  src/main/kotlin/.../ReportService.kt:88
    .block() on a Mono → blocks the event-loop thread
    Fix: await the value in the suspend chain / return the Mono
```
