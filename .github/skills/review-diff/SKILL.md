---
name: review-diff
description: Comprehensive multi-aspect review of the current local changes (git diff) via specialized subagents — bugs & conventions, tests, comments, silent failures, type design, then simplification. Prints a report; does not touch GitHub. Renamed copy of Anthropic's pr-review-toolkit. Use for a thorough review before opening a PR. For an existing GitHub PR, use `review-pr`.
tags: [meta]
---

# Skill: review-diff

> **Attribution:** Adapted from Anthropic's official **`pr-review-toolkit`** plugin
> (`claude-plugins-official`), licensed Apache 2.0 — see `LICENSE` in this directory.
> Changes from the original: renamed to `review-diff` (it reviews the local `git diff`
> and prints a report — it does not post to GitHub; the GitHub-PR commenter is the
> separate `review-pr` skill); frontmatter converted to this repo's skill format;
> agents load from `.github/agents/` (symlinked at `.claude/agents/`). The agent files
> mention `CLAUDE.md` — in this project that means **`AGENTS.md`**; tell each agent so.

Run a comprehensive review of the current local changes (the `git diff`) using multiple specialized agents, each focusing on a different aspect of code quality.

The agents this skill drives live in `.github/agents/` (registered via the `.claude/agents` symlink): `code-reviewer`, `pr-test-analyzer`, `comment-analyzer`, `silent-failure-hunter`, `type-design-analyzer`, `code-simplifier`. When launching any of them, tell it this repo's conventions are in **`AGENTS.md`** (not `CLAUDE.md`).

**Review Aspects (optional):** "$ARGUMENTS"

## Review Workflow

1. **Determine Review Scope**
   - Check git status to identify changed files
   - Parse arguments to see if user requested specific review aspects
   - Default: Run all applicable reviews

2. **Available Review Aspects**
   - **comments** — Analyze code comment accuracy and maintainability (`comment-analyzer`)
   - **tests** — Review test coverage quality and completeness (`pr-test-analyzer`)
   - **errors** — Check error handling for silent failures (`silent-failure-hunter`)
   - **types** — Analyze type design and invariants, if new types added (`type-design-analyzer`)
   - **code** — General code review for project guidelines (`code-reviewer`)
   - **simplify** — Simplify code for clarity and maintainability (`code-simplifier`)
   - **all** — Run all applicable reviews (default)

3. **Identify Changed Files**
   - Run `git diff --name-only` to see modified files
   - Check if a PR already exists: `gh pr view`
   - Identify file types and what reviews apply

4. **Determine Applicable Reviews**
   - **Always applicable**: `code-reviewer` (general quality)
   - **If test files changed**: `pr-test-analyzer`
   - **If comments/docs added**: `comment-analyzer`
   - **If error handling changed**: `silent-failure-hunter`
   - **If types added/modified**: `type-design-analyzer`
   - **After passing review**: `code-simplifier` (polish and refine)

5. **Launch Review Agents** — sequential (one at a time, easier to act on) or parallel (faster; user can request `all parallel`).

6. **Aggregate Results** — group as Critical (must fix), Important (should fix), Suggestions (nice to have), and Strengths.

7. **Provide Action Plan**
   ```markdown
   # PR Review Summary

   ## Critical Issues (X found)
   - [agent-name]: Issue description [file:line]

   ## Important Issues (X found)
   - [agent-name]: Issue description [file:line]

   ## Suggestions (X found)
   - [agent-name]: Suggestion [file:line]

   ## Strengths
   - What's well-done in this PR

   ## Recommended Action
   1. Fix critical issues first
   2. Address important issues
   3. Consider suggestions
   4. Re-run review after fixes
   ```

## Usage Examples

- Full review (default): invoke with no arguments.
- Specific aspects: `tests errors`, `comments`, `simplify`.
- Parallel: `all parallel` — launches all agents at once.

## Notes

- Agents analyze the `git diff` by default — run early, before creating the PR.
- This is a **general** reviewer (bugs/tests/types/comments). For this project's domain conventions, also run the `audit` bundles (e.g. `idor-audit`, `migration-safety-audit`).
- Tag is `[meta]`, so this skill is **not** part of `audit all`.
