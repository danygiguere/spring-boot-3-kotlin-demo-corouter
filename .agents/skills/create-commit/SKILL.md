---
name: create-commit
description: Suggère un message de commit prêt à copier-coller — ne committe jamais, ne stage jamais. L'utilisateur exécute lui-même la commande. Use when the user wants to commit their current changes.
allowed-tools: Bash(python3:*)
---

# Create commit (suggestion only)

**Never run `git add` or `git commit`.** The user runs the command themselves.

## Steps

1. Run the preflight script (one call replaces git status + diff + log):
   ```bash
   python3 .agents/scripts/preflight_commit.py
   ```
   If it prints `NOTHING TO COMMIT`, report that and stop.

2. From the output, write ONE commit message matching the repo style:
   - Subject: imperative mood, capitalised, ≤ 50 chars, no trailing period, no Jira key
     prefix, no conventional-commit prefix (`feat:` etc.)
   - Describe what the diff *actually does*, not the story title
   - Optional body: bullet list after a blank line, only if the diff spans several distinct changes

3. Present the ready-to-run command in a copyable block:
   ```bash
   git add -A && git commit -m "Subject here"
   ```
   Or with a body:
   ```bash
   git add -A && git commit -m "Subject here" -m "- point one\n- point two"
   ```

4. Stop. Do not commit, do not push. The user copies and runs the command.
