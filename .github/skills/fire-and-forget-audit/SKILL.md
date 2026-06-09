---
name: fire-and-forget-audit
description: Find cold Flows never collected and Mono/Flux producers never awaited/returned — the operation silently never runs. Use when adding reactive service calls or chaining repository writes.
tags: [correctness]
---

# Skill: fire-and-forget-audit

Find discarded reactive/cold producers — a no-op where work silently never happens.

Canonical rules: `AGENTS.md` §"Reactive — no fire-and-forget". This is the on-demand check.

## Rules

- Be ultra concise in all output.

## When to use

Run when adding/reviewing service code that calls cold producers (Flow-returning repos/services) or Reactor publishers from interop (`TransactionalOperator`, `WebClient`).

## Instructions

1. Grep `src/main/kotlin` for producers whose result is **discarded** — a statement on its own line, not `return`ed, assigned, awaited, or collected:
   - a `Flow`-returning call (`repository.findAllByX(...)`, a service `fun … : Flow<T>`) never `.collect`/`.toList()`/terminated → **cold Flow, query never runs**
   - a `Mono`/`Flux` from interop created but never `.await*()`ed / returned / composed
   - giveaway: a producer line with no `return`, no `val x =`, no `.await*()`, no `.collect`/`.toList()`
2. Flag bare `.subscribe()` on a Reactor publisher — runs the work but swallows errors and drops the reactive context (tx, correlation). Compose into the returned pipeline / await instead.
3. Correct terminals: a suspend `CoroutineCrudRepository` call (`save`/`delete`) already executes; for Mono use `.awaitSingleOrNull()`/`.awaitFirstOrNull()`; for Flow use `.collect`/`.toList()`, or return the value in the chain. Flag a producer with none.
4. Skip genuine intentional fire-and-forget (rare; must be commented and error-handled).

## Output format

```
Fire-and-Forget Audit
=====================
Discarded producers: N

  src/main/kotlin/.../SomeService.kt:NN
    teamMemberRepository.findAllByTeamId(id)   ← cold Flow never collected → query never runs
    Fix: .toList() / .collect { }, or return the Flow

  src/main/kotlin/.../NotifyService.kt:40
    someMono.subscribe()   ← fire-and-forget; errors lost, no tx/correlation
    Fix: someMono.awaitSingleOrNull(), or compose into the returned pipeline
```
