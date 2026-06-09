---
name: nplus1-audit
description: Find likely N+1 query patterns in reactive Kotlin code. Use when reviewing services that fetch related entities for collections.
tags: [scaling]
---

# Skill: nplus1-audit

Find likely N+1 query patterns in reactive Kotlin services.

Canonical rules: `AGENTS.md` §"N+1 queries". This skill is the on-demand check.

## Rules

- Be ultra concise in all output.

## When to use

Run when reviewing services that aggregate related data for collections, or before merging changes to repository or service layers.

## Instructions

1. Grep `src/main/kotlin/**/*Service.kt` for a per-item DB call inside a collection traversal:
   - `.map {` / `.forEach {` / `for (x in items)` whose body calls `repository.findX(...)` / `findById(...)` (one query per element)
   - a `Flow` collected with `.toList()` then mapped with an inner `repository.find…` per element
   - any `suspend` repo call inside a lambda iterating a collection
2. For each match, locate the enclosing function and check whether a batch `findAllByXIn(ids)` method exists (or should) on the repository.
3. Report:
   - **Likely N+1:** per-item repository call inside a collection traversal
   - **Suggestion:** batch with `findAllByXIn(ids)`, then `groupBy` / `associateBy` the FK in memory
4. Canonical correct shape: `EnterpriseService.searchByName` and `TeamService.findTeamsWithEnterpriseInfo` — fetch the collection, collect ids, one `…In(ids)` query per relation, group in memory. Query count is constant regardless of result size.
5. Skip intentionally per-item calls (rare; should be commented).

## Output format

```
N+1 Audit Results
=================
Suspicious patterns: N

  src/main/kotlin/.../SomeService.kt:NN
    Pattern: teams.map { enterpriseRepository.findById(it.enterpriseId) }  ← 1 query per team
    Suggest: enterpriseRepository.findAllById(ids), then associateBy { it.id } in memory
```
