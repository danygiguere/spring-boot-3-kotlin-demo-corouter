---
name: observability-audit
description: Find errors that won't surface in logs — swallowed exceptions, wrong log levels, secrets/PII in logs, raw println, lost causes. Use when adding error handling, a catch, or a logger call.
tags: [scaling]
---

# Skill: observability-audit

Find logging/error-reporting gaps that hide failures, or leak data into the logs.

Canonical rules: `AGENTS.md` §"Observability — logging". This skill is the on-demand check.

## Rules

- Be ultra concise in all output.

## When to use

Run when adding/reviewing error handling, a `catch` / `onErrorResume` / `onErrorComplete`, or new logger statements.

## Instructions

1. **Swallowed exceptions** (highest priority — invisible failures). Grep `src/main/kotlin` for:
   - empty/blind catches: `catch (...) { }`, or a catch returning a default / `null` without logging or rethrowing
   - reactive drops on interop: `onErrorResume { Mono.empty() }`, `onErrorComplete()`, `onErrorReturn(...)` discarding a genuine error
   Flag any where the error neither propagates to `ApplicationExceptionHandler`, gets logged with its cause, nor is a deliberate, commented no-op.
2. **Bypassing the central handler** — business failures should `throw AppException.*` and let `ApplicationExceptionHandler` log (with `correlationId`) and respond. Flag ad-hoc error responses built in handlers/services that bypass it (overlaps `exception-audit`).
3. **Wrong log level** — `logger.error` on an expected/business 4xx-shaped path → noise; a real failure logged at `info`/`debug` → won't surface. Flag mismatches. (The handler logs 4xx at `warn`, 5xx at `error` — match that.)
4. **Secrets / PII in logs** — flag logger calls interpolating tokens/passwords, or PII (`email`, `phoneNumber`, a full request/DTO body).
5. **Raw output** — flag `println(` / `System.out` / `System.err`; use `KotlinLogging.logger {}` (the project standard).
6. **Lost cause** — a caught error rethrown/wrapped without passing `cause` (`AppException.*(key, args, e)`) → breaks the stacktrace.

## Output format

```
Observability Audit
===================
Files scanned: N

  src/main/kotlin/.../SomeService.kt:64
    catch (e: Exception) { return null } — error swallowed
    Impact: failure invisible in logs
    Fix: logger.error(e) { "[$correlationId] ..." } and rethrow, or let it reach ApplicationExceptionHandler

  src/main/kotlin/.../FooService.kt:30
    println("loaded $user") — raw output + PII (email/phone)
    Fix: KotlinLogging logger; don't log PII
```
