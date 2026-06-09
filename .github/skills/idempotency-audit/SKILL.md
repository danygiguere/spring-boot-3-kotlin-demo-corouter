---
name: idempotency-audit
description: Find writes that aren't replay-safe — create-once invariants with no DB unique constraint, retriable creates that duplicate rows, and (when added) external mutations or webhooks without dedup. Use when adding a POST endpoint, a uniqueness rule, an external SDK call, or a webhook.
tags: [correctness]
---

# Skill: idempotency-audit

Find writes that aren't safe to retry — a client/proxy retry or provider redelivery must not duplicate work.

Canonical rules: `AGENTS.md` §"Idempotency — replay-safe writes". This skill is the on-demand check.

> This project has **no external mutating SDKs or webhooks yet.** Steps 1–2 are live today; steps 3–4 are forward-looking and apply the moment such an integration lands.

## Rules

- Be ultra concise in all output.

## When to use

Run when adding/reviewing a create endpoint, a "one per X" uniqueness rule, an external mutating call, or a webhook handler.

## Instructions

1. **Create-once invariants** — for every "at most one row per X" rule (unique email, one membership per team+user), verify a DB `UNIQUE` constraint backs it. An app-level pre-check (`findByX != null → Conflict`) alone races under concurrent requests. Canonical shape: `uq_team_members_team_user` + `UserService.assignToTeam` catching `DataIntegrityViolationException` → `AppException.Conflict(key, args, e)`. Flag a pre-check with no constraint behind it.
2. **Retriable creates** — a `POST` that inserts a row with no natural unique key duplicates the row on client/proxy retry. Flag only where a duplicate is *incorrect* (a second membership, a second refund) — a duplicate `Team` named "QA" is the client's business. Fix: a natural-key `UNIQUE` constraint, not an ad-hoc pre-check.
3. **External mutating calls** (forward-looking) — any create/charge/send call into an external SDK must carry a deterministic idempotency key (e.g. `setIdempotencyKey("refund-{id}")`) or a persisted-external-id guard (skip if `x.externalId != null`). Flag calls with neither. Ties into `AGENTS.md` §"Atomicity & transactions": external mutations stay outside the tx and reconcile on retry.
4. **Webhook handlers** (forward-looking) — each must dedupe provider redelivery by event id before processing: an inbox row via `INSERT … ON CONFLICT (event_id) DO NOTHING`, no-op when the insert is skipped. Flag processing without an event-id check.

## Output format

```
Idempotency Audit
=================
Create-once invariants: N   Retriable creates: M   External calls/webhooks: 0 (none yet)

  src/main/kotlin/.../SomeService.kt:33  registerMember()
    pre-check findByTeamIdAndUserId != null → Conflict, but no UNIQUE constraint on (team_id, user_id)
    Risk: two concurrent requests both pass the check → duplicate row
    Fix: UNIQUE constraint + catch DataIntegrityViolationException → AppException.Conflict (see assignToTeam)

  ✅ uq_team_members_team_user backs assignToTeam — canonical shape
```
