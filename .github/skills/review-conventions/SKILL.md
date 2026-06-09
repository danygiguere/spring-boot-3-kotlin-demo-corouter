---
name: review-conventions
description: Project-specific review router — inspect the current diff and run only the relevant audit skills for each changed file type. Use to review a branch/PR/staged changes for convention compliance. Complements (does not replace) the built-in generic /code-review.
tags: [meta]
---

# Skill: review-conventions

Route a diff to this project's audit skills. This does **not** re-do generic correctness review — run the built-in `/code-review` (and `/security-review`) for that first; this layer checks the conventions in `AGENTS.md` that the generic reviewer can't know.

## Rules

- Be ultra concise in all output.
- Don't restate generic findings; only report convention violations from the mapped audits.

## When to use

Reviewing a branch, PR, or staged/working-tree changes.

## Instructions

1. **Get the changed files.** `git diff --name-only <base>...HEAD` (or `git diff --name-only --staged` / working tree). Keep the list.
2. **Bucket each file and run the mapped audits** (run each matched skill once, scoped to the changed files):

   | Changed file | Run |
   |---|---|
   | `service/*Service.kt` | `atomicity-audit`, `blocking-call-audit`, `exception-audit`, `fire-and-forget-audit`, `idempotency-audit`, `nplus1-audit`, `observability-audit`, `stateless-audit` |
   | `handler/*Handler.kt` | `idor-audit`, `response-exposure-audit`, `exception-audit`, `observability-audit` |
   | request DTO (`dto/*Request.kt`) | `mass-assignment-audit`, `idor-audit` |
   | response / projection DTO (`dto/*Response.kt`, `*WithTeams.kt`, `*Summary.kt`) or an entity returned by a handler | `response-exposure-audit` |
   | `repository/*Repository.kt` | `nplus1-audit` |
   | `@Component` / `@Service` adding state or `@Scheduled` | `stateless-audit` |
   | `exception/*` changes | `exception-audit`, `observability-audit` |
   | `messages*.properties` | `exception-audit` (key present in both bundles; renamed key = `type` URI contract change) |
   | `db/migration/V*.sql` | `migration-safety-audit` |

3. **Deduplicate** — if several buckets pull the same audit, run it once over the union of files.
4. **Aggregate** findings by skill; drop any audit whose file type isn't in the diff (don't run the full suite blindly).

## Output format

```
Review (convention pass) — base...HEAD
======================================
Changed: 4 files → ran 5 audits

atomicity-audit         ❌ SomeService.reassignMembers(): 2 writes, no tx
nplus1-audit            ✅ clean
response-exposure-audit ⚠️ User entity returned exposes passwordHash — scope via a *Response DTO
mass-assignment-audit   ✅ clean
exception-audit         ✅ clean

Run generic /code-review separately for correctness bugs, and security-audit (or /security-review) for vulnerabilities.
```
