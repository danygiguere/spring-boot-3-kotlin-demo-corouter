---
name: stateless-audit
description: Find in-process state that breaks horizontal scaling — @Scheduled, and authoritative/consistency-critical data held in static fields, singleton maps, or instance caches instead of the DB. Use when adding a cache, a counter, a scheduler, or any field that holds mutable state.
tags: [scaling]
---

# Skill: stateless-audit

Find state that assumes a single instance. If more than one instance runs behind a load balancer, any request may land on any instance.

Canonical rules: `AGENTS.md` §"Stateless architecture". This skill is the on-demand check. (This project is infra-agnostic — the rule is "keep instances stateless"; the concrete shared store, scheduler, or lock is a deployment choice.)

## Rules

- Be ultra concise in all output.

## When to use

Run when adding/reviewing: a `@Scheduled` job, an in-memory cache (Caffeine, `ConcurrentHashMap`, `mutableMapOf`/`mutableListOf` held as a field), a counter/flag (`Atomic*`, `@Volatile`, `var` field), or anything storing request/business data outside the DB.

## Instructions

1. **`@Scheduled`** — grep `src/main/kotlin` for `@Scheduled`. If the app may run more than one instance, an in-process schedule fires on **every** instance, duplicating work. Flag active (non-commented) usage; drive periodic work from a single external trigger (one scheduler hitting an endpoint) or guard it with a shared lock.
2. **In-process state** — grep for state held as class/top-level fields: `Caffeine`, `ConcurrentHashMap`, `AtomicInteger/AtomicLong/AtomicReference`, `@Volatile`, `private val … = mutableMapOf/mutableListOf`, mutable `var` fields on `@Service`/`@Component`. Classify each:
   - ✅ **OK** — a local cache of bounded-staleness data whose authority lives in the DB (or an external source), with a short TTL, where per-instance staleness is acceptable. Immutable derived data is also OK.
   - ❌ **Violation** — authoritative or consistency-critical state held only in memory: if losing it on restart, or two instances disagreeing, is incorrect (not just a cache miss), it must move to the DB or a shared store.
   - ⚠️ **Flag** — survives restart fine, but sharding it across instances changes behavior (rate limiters → ≈N× limit, circuit breakers trip per-node, dedup windows). Call out the tradeoff so the owner accepts it or moves it to a shared store.
3. Don't flag: `val` immutable config/dependencies, request-scoped locals, DB/R2DBC access.

## Output format

```
Stateless Audit
===============
@Scheduled: N    In-process state holders: M

  src/main/kotlin/.../FooScheduler.kt:21  @Scheduled(cron=...)
    ❌ fires on every instance if scaled — move to a single external trigger or a shared lock

  src/main/kotlin/.../RateLimitFilter.kt:30  ConcurrentHashMap attempts
    ⚠️ per-instance counter → effective limit ≈ N× across N instances. Accept, or move to a shared store.
```
