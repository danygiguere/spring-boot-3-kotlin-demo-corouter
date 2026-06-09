---
name: response-exposure-audit
description: Enforce the REST-direct response pattern — endpoints return a *Response DTO directly (never an @Table entity, never an envelope wrapper) and leak no secrets/internal fields. Outbound counterpart to idor-audit.
tags: [security]
---

# Skill: response-exposure-audit

Enforce the response pattern and find over-exposed data on the way out — the outbound counterpart to IDOR.

Canonical rules: `AGENTS.md` §"Response shape — REST-direct". This is the on-demand check; the inbound counterparts are [`mass-assignment-audit`](../mass-assignment-audit/SKILL.md) and [`idor-audit`](../idor-audit/SKILL.md).

## Rules

- Be ultra concise in all output.

## When to use

Run when adding/reviewing a response DTO, a handler return body, or a mapping from entity → API response.

## Instructions

The pattern (REST-direct): an endpoint returns **the resource representation directly** — a `*Response` DTO (`UserResponse`, `TeamResponse`, …) or an existing projection DTO (`EnterpriseWithTeams`, `TeamWithMembers`, `TeamSummary`) — with the HTTP status carrying the semantics. No success envelope.

1. **Entity returned directly** — flag a handler whose response body is an `@Table` entity (`User`, `Team`, `Enterprise`, `TeamMember`) or a `Flow<Entity>`. Map it first via `toResponse()`: `bodyValueAndAwait(x.toResponse())` for one, `bodyAndAwait(flow.map { it.toResponse() })` for a stream.
2. **Success envelope** — flag a generic wrapper (`DataResponse`, `MessageResponse`, `ApiResponse<T>`, `{ data, message }`) used for success. Return the representation directly; let the status code carry meaning (`200`/`201`; `201`/`204` with no body for a no-payload action like `assignToTeam`). An envelope is acceptable **only** for a paginated collection (one that carries real page metadata) — note it, don't flag.
3. **Secret / internal fields on a response shape** — flag any of these on a type serialized to the client:
   - credentials/identity: `passwordHash`, `password`, raw tokens/JWT, provider/SSO ids, `*Secret`
   - internal bookkeeping: `deletedAt` / audit columns, internal-only flags
4. **Cross-user leakage** — a list/detail response including another principal's PII (`email`, `phoneNumber`) the caller shouldn't see; scope the DTO to what's viewable.
5. Only flag **outbound** (response) shapes — inbound DTOs are `mass-assignment-audit` / `idor-audit`.

## Output format

```
Response-Exposure Audit
=======================
Response shapes scanned: N

  src/main/kotlin/.../SomeHandler.kt:NN
    returns the Team entity directly — couples API to the table; future columns leak
    Fix: bodyValueAndAwait(team.toResponse())

  src/main/kotlin/.../FooHandler.kt:40
    wraps the payload in DataResponse(...) — envelope on a non-paginated response
    Fix: return the DTO directly; let the status code carry meaning

  src/main/kotlin/.../dto/SomeDto.kt:18
    field passwordHash: String — secret on a response shape
    Fix: remove from the response shape
```
