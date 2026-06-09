---
name: audit
description: Umbrella runner for the project's audit skills. Run a bundle (security|correctness|scaling|db|all) over a scope (the current diff by default, or a layer / path / whole repo). Fans out one subagent per skill in parallel, then dedups + severity-sorts. `audit` with no args defers to review-changes (diff). Use for a focused or full security/quality sweep.
argument-hint: list | security | correctness | scaling | db | all [scope]
tags: [meta]
---

# Skill: audit

Orchestrator over `.github/skills/*` audit skills. Selects skills by **bundle tag**, runs them over a **scope**, aggregates the findings. It does not contain audit logic itself — it delegates to each skill's own `SKILL.md`.

## Rules
- Be ultra concise in output.
- Never blind-run every skill on a plain diff — the default everyday path is relevance-based (`review-changes`). Full-scope sweeps must name a bundle + scope.
- Bundles are derived from each skill's `tags:` frontmatter — **never hardcode the skill list here.** A new `*-audit` skill auto-joins its bundle.

## Commands
Run a bundle on your current changes (the diff) — the everyday case:

| Command | Skills it runs |
|---|---|
| `audit` | router — only the audits matching your changed files (via `review-changes`) |
| `audit security` | `idor-audit` · `mass-assignment-audit` · `response-exposure-audit` · `security-audit` |
| `audit correctness` | `atomicity-audit` · `exception-audit` · `fire-and-forget-audit` |
| `audit scaling` | `blocking-call-audit` · `nplus1-audit` · `observability-audit` · `stateless-audit` |
| `audit db` | `migration-safety-audit` |
| `audit all` | all of the above (every non-`meta` skill) |
| `audit list` | — prints the live bundle→skills map (source of truth) |

The skill lists are **auto-derived from each skill's `tags:`** — they stay current as skills are added/removed; `audit list` prints the live set. A scope (diff vs a layer) only changes *where* the skills run, not *which*.

## Scope it wider (optional)
Add a **scope token** after the bundle:

| Command | Runs on |
|---|---|
| `audit security service` | the `service/` layer |
| `audit scaling handler` | the `handler/` layer |
| `audit all .` | the whole repo |

Scope tokens: a **layer name** (`router`, `handler`, `service`, `repository`, `domain`, `dto`, `exception`, `extension`, `config`) resolves to that package; **`.`** = the whole backend (`src/main/kotlin`). (A literal path still works for anything custom.) Bigger scope = slower/costlier — prefer the narrowest that covers your change.

## Instructions
1. **Parse args** → `bundle`, `scope-token` (optional).
   - `list` → do step 2, print the tag→skills map (+ each skill's one-line description) and the **Commands** + **Scope it wider** tables, STOP.
   - no args → invoke `review-changes`, STOP.
2. **Discover** skills: glob `.github/skills/*/SKILL.md`; read each frontmatter `name`, `description`, `tags`. Build `tag → [skills]`. (Self-maintaining — reflects whatever skills exist now.)
3. **Select** skills:
   - `all` → every skill whose `tags` is not `[meta]`.
   - named bundle → skills whose `tags` contains it. Unknown bundle → print known bundles, STOP.
4. **Resolve the scope token** → a path (or the diff):
   - omitted → the diff. Compute changed files (`git diff --name-only` / `<base>...HEAD` / `--staged`); hand each skill the diff + file list. If the tree is clean, say "no changes — pass a layer name or `.`" and STOP.
   - `.` → `src/main/kotlin`.
   - a **layer name** (`router`/`handler`/`service`/`repository`/`domain`/`dto`/`exception`/`extension`/`config`) → `src/main/kotlin/com/example/corouterdemo/<token>`.
   - otherwise, if it's an existing path, use it literally. Else → list the layer names above and STOP.
   - For any path scope (not the diff), run each skill over the whole subtree (ignore its "diff-only" framing).
5. **Run — fan out one subagent per selected skill, in parallel.** Each subagent: read that skill's `SKILL.md`, follow its Instructions against the scope, return findings in that skill's own output format. If subagents are unavailable, run sequentially. Cap: if a bundle selects >8 skills over a large scope, say so first.
6. **Warn before `all .`** (every skill × whole repo is expensive): state "N skills × ~M files" and proceed.
7. **Aggregate**: dedup findings that point at the same `file:line` + issue (skills overlap — e.g. `idor-audit` and `security-audit` may both flag one IDOR); severity-sort (❌ > ⚠️ > ✅); group by skill.

## Output format
```
Audit — bundle=security  scope=handler
======================================
ran 4 skills (parallel) · 1 ⚠️  3 ✅

idor-audit              ⚠️ UserHandler.findById — no ownership check (forward-looking; no auth yet)
mass-assignment-audit   ✅ clean
response-exposure-audit ✅ clean
security-audit          ✅ clean
```

## Notes
- Complements (doesn't replace): `review-changes` (fast diff router) and the built-in `/code-review` + `/security-review` (generic). `audit` is for running the project's domain audits at **bundle / layer / repo** scope.
- Bundles auto-derive from `tags:`; there is currently no `contracts` bundle (no contract/API-sync skill in this project) — add one and it appears automatically.
