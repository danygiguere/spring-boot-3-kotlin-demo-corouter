---
name: atomicity-audit
description: Find service methods with 2+ DB mutations that aren't atomic, and transaction-boundary traps. Use when reviewing or adding multi-write service logic, or before merging changes to service-layer code.
tags: [correctness]
---

# Skill: atomicity-audit

Find non-atomic multi-write flows and broken transaction boundaries in reactive Kotlin services.

Canonical rules: `AGENTS.md` §"Atomicity & transactions". This skill is the on-demand check — it does not restate the rules.

## Rules

- Be ultra concise in all output.

## When to use

Run when reviewing or adding service methods that write to 2+ rows/entities, or before merging service-layer changes.

## Instructions

1. Grep `src/main/kotlin/**/*Service.kt` for methods that perform **2+ DB mutations** (any two of `save`, `saveAll`, `delete`, `deleteByX`, `.update`, `insert` in one method body).
2. For each, check the method is covered by a transaction boundary:
   - `@Transactional` on the **public** entry point — flag if it's on a `private` method or reached only by **self-invocation** (same-class call); the proxy silently ignores both.
   - or `transactionalOperator.executeAndAwait { … }` wrapping the writes.
3. Flag the known traps:
   - **External call inside the tx** — a blocking/HTTP/email/object-storage/payment-SDK call *between* DB writes inside a `@Transactional`/`executeAndAwait` block. External calls belong outside; side effects go after commit.
   - **`withContext(Dispatchers.IO)` treated as a boundary** — DB writes grouped only by a `withContext` with no enclosing tx. It is a thread switch, not a transaction.
   - **delete-then-save / default-flag swap / parent-then-children pair** not wrapped.
4. Skip read-only methods and single-mutation methods.

## Output format

```
Atomicity Audit
===============
Multi-write methods scanned: N

  src/main/kotlin/.../SomeService.kt:NN  reassignMembers()
    2 mutations (teamMemberRepository.deleteByTeamId + saveAll), no @Transactional / executeAndAwait
    Risk: partial write if the second op fails
    Fix: wrap writes in transactionalOperator.executeAndAwait { } (self-invoked here)
```
