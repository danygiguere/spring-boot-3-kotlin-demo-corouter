---
name: exception-audit
description: Find Kotlin code that throws non-AppException exceptions. Use when asked to verify that all error handling follows the AppException pattern.
tags: [correctness]
---

# Skill: exception-audit

Find Kotlin code that throws non-`AppException` exceptions.

Canonical rules: `AGENTS.md` §"Error handling". This skill is the on-demand check.

## Rules

- Be ultra concise in all output.

## When to use

Run when you want to verify that all error handling follows the `AppException.*` pattern.

## Instructions

1. Search for all `throw ` statements in `src/main/kotlin/**/*.kt`.
2. For each throw site, classify it:
   - **OK:** `AppException.*` (`NotFound`, `BadRequest`, `Forbidden`, `Conflict`, `ValidationErrors`, …)
   - **OK (intentional):** `ConstraintViolationException` (validation), `AccessDeniedException`
   - **OK (stub):** `NotImplementedError` / `TODO()` inside a method body with a comment
   - **REVIEW:** anything else — report file path, line number, and the throw expression
3. Also flag a caught error rethrown without its `cause`: prefer `AppException.*(key, args, e)` (see `UserService.assignToTeam` catching `DataIntegrityViolationException`).
4. **i18n keys:** every key passed to `AppException.*` must exist in **both** `messages.properties` and `messages_fr.properties` — a missing key reaches the client raw. Keys also derive the RFC 9457 `type` URI (`error.x.y` → `/errors/x-y`), so flag a renamed key as an API contract change.
5. Summarise counts before listing flagged items.

## Output format

```
Exception Audit Results
=======================
Total throw sites: N
  AppException.*: N  ✅
  Intentional (Constraint/Access): N  ✅
  Stubs (NotImplementedError/TODO): N  ✅
  Needs review: N  ⚠️
  Missing i18n keys: N  ⚠️

Needs review:
  src/main/kotlin/.../SomeService.kt:42
    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "message")
    Fix: throw AppException.BadRequest("error.some.key")

Missing i18n keys:
  error.team.not.found (TeamService.kt:31) — absent from messages_fr.properties
```
