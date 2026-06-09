---
name: idor-audit
description: Find handlers/services that take a resource id without verifying it belongs to the authenticated principal. Use when adding an endpoint that accepts an id, or trusting a client-supplied userId/ownerId.
tags: [security]
---

# Skill: idor-audit

Find Insecure Direct Object Reference gaps — id resolves but ownership isn't checked.

Canonical rules: `AGENTS.md` §"Security: IDOR". This skill is the on-demand check.

> This project has **no authentication yet** (AGENTS.md §"Security: IDOR"). With no principal there is nothing to enforce ownership against, so today this audit is **forward-looking**: it inventories the id-taking surface that will need ownership checks the moment auth lands, and flags client-supplied identity that must move to the auth context then.

## Rules

- Be ultra concise in all output.

## When to use

Run when adding/reviewing any handler or service that accepts a resource id (`{id}`, `{userId}`, `{teamId}`…) or reads an owner/identity from the request.

## Instructions

1. **Client-supplied identity** — grep request DTOs / handlers / path vars for identity or ownership taken from the client: `userId`, `ownerId`, `enterpriseId`-as-authorization, `role`, `status`. Today these are unavoidable (no principal); record each as a **must-revisit-when-auth-lands** site — once auth exists the principal comes from the auth context, never the payload.
2. **Id without ownership check** — for handlers/services taking a resource id, note that `findById(id)` returns any row to any caller, and ids are sequential `BIGINT` → enumerable. When auth lands, scope the query (`findByIdAndOwnerId…`) or pass the principal id and assert ownership/membership.
3. Confirm the eventual failure mode: ownership mismatch should throw `AppException.Forbidden(...)` (or `NotFound` when existence itself is sensitive) — not return the row.

## Output format

```
IDOR Audit (forward-looking — no auth yet)
==========================================
Id-taking handlers scanned: N

  src/main/kotlin/.../UserHandler.kt:32  findById(id)
    userService.findById(id) returns any user to any caller
    When auth lands: scope to the principal (findByIdAndOwnerId / assert + Forbidden)

  src/main/kotlin/.../TeamRequest.kt:6
    enterpriseId taken from body → when auth lands, derive/verify from the principal, don't trust the payload
```
