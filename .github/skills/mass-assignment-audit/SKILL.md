---
name: mass-assignment-audit
description: Find server-owned fields (privilege/lifecycle flags, server bookkeeping) in request DTOs — mass-assignment / privilege escalation. Use when adding or changing a request DTO.
tags: [security]
---

# Skill: mass-assignment-audit

Find request DTOs that accept fields the server must own — the general mass-assignment / privilege-escalation case.

Canonical rules: `AGENTS.md` §"Mass assignment — never trust client-controlled input". This is the on-demand check for that rule. Ownership/identity ids (`userId`/`ownerId`/`enterpriseId`-as-authorization) are deferred to [`idor-audit`](../idor-audit/SKILL.md); this skill owns **state/role/privilege flags** and **server bookkeeping**.

## Rules

- Be ultra concise in all output.

## When to use

Run when adding or changing any `*Request` DTO (or other inbound, request-body-bound type).

## Instructions

1. Scope to **inbound** types only: `data class *Request` and anything bound from a request body (`awaitBody<T>()`). A field on a **response** DTO is fine — `id`/timestamps legitimately appear there.
2. Flag a request DTO property matching a server-owned field:
   - **State/role/privilege flags:** `role`, `status`, `isAdmin`, `enabled`, `verified`, `emailVerified`, and other lifecycle/privilege flags.
   - **Server bookkeeping:** `id`, `createdAt`, `updatedAt`, `deletedAt`, computed counts/ratings.
3. **Apply judgment — legit vs escalation.** An admin/role-guarded endpoint whose actual purpose is to set such a field is legit (note it, don't flag). A self-service endpoint where the field would let the client escalate (`role`, `verified`, a status transition it shouldn't drive) → **flag**.
4. Fix: remove the field; set it server-side. Entities default `@Id` to `null` and the DB assigns it; flags/timestamps are server-derived.
5. Current `UserRequest` / `TeamRequest` / `EnterpriseRequest` correctly exclude `id` and timestamps — keep it that way.

## Output format

```
Mass-Assignment Audit
=====================
Inbound request DTOs scanned: N

  src/main/kotlin/.../dto/UserRequest.kt:9
    verified: Boolean   ← privilege flag on a self-service DTO
    Risk: client marks itself verified
    Fix: remove; server sets it after the verification flow

  ✅ UserRequest / TeamRequest / EnterpriseRequest exclude id + timestamps — correct
```
