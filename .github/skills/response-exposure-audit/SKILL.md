---
name: response-exposure-audit
description: Enforce response hygiene — responses leak no secrets/internal fields and no other principal's PII, and use no success envelope. Returning an @Table entity directly is allowed when it carries no sensitive data. Outbound counterpart to idor-audit.
tags: [security]
---

# Skill: response-exposure-audit

Find over-exposed data on the way out — the outbound counterpart to IDOR.

Canonical rules: `AGENTS.md` §"Response shape". This is the on-demand check; the inbound counterparts are [`mass-assignment-audit`](../mass-assignment-audit/SKILL.md) and [`idor-audit`](../idor-audit/SKILL.md).

## Rules

- Be ultra concise in all output.

## When to use

Run when adding/reviewing a response DTO, a handler return body, or a mapping from entity → API response.

## Instructions

The pattern: an endpoint returns **the resource representation directly** — an `@Table` entity (when it carries no sensitive data), a `*Response` DTO, or a projection DTO (`EnterpriseWithTeams`, `TeamWithMembers`, `TeamSummary`) — with the HTTP status carrying the semantics. No success envelope. Returning an entity directly is **fine**; the question is only *what fields it exposes*.

1. **Secret / internal fields reaching the client** — flag any of these on a type serialized to a response (entity, DTO, or projection):
   - credentials/identity: `passwordHash`, `password`, raw tokens/JWT, provider/SSO ids, `*Secret`
   - internal bookkeeping: `deletedAt` / audit columns, internal-only flags

   This is the core check. When a returned **entity** carries such a field, the fix is to stop returning the entity and add a scoped `*Response` DTO (`bodyValueAndAwait(x.toResponse())`) — the entity itself is not the problem, the exposed field is.
2. **Success envelope** — flag a generic wrapper (`DataResponse`, `MessageResponse`, `ApiResponse<T>`, `{ data, message }`) used for success. Return the representation directly; let the status code carry meaning (`200`/`201`; `201`/`204` with no body for a no-payload action like `assignToTeam`). An envelope is acceptable **only** for a paginated collection (one that carries real page metadata) — note it, don't flag.
3. **Cross-user leakage** — a list/detail response including another principal's PII (`email`, `phoneNumber`) the caller shouldn't see; scope the response to what's viewable.
4. Only flag **outbound** (response) shapes — inbound DTOs are `mass-assignment-audit` / `idor-audit`.

## Output format

```
Response-Exposure Audit
=======================
Response shapes scanned: N

  src/main/kotlin/.../domain/entity/User.kt:9
    entity returned by UserHandler now carries passwordHash — secret reaches the client
    Fix: stop returning the entity; add a scoped UserResponse via toResponse()

  src/main/kotlin/.../FooHandler.kt:40
    wraps the payload in DataResponse(...) — envelope on a non-paginated response
    Fix: return the representation directly; let the status code carry meaning

  src/main/kotlin/.../dto/SomeDto.kt:18
    field passwordHash: String — secret on a response shape
    Fix: remove from the response shape
```
