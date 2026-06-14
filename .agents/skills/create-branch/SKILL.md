---
name: create-branch
description: Crée/bascule sur la branche d'un récit Jira (ex. PANCCAED-1) depuis le HEAD courant, en conservant les modifications non commitées. Use at the start of work on a Jira story. Ne commit rien.
allowed-tools: Bash(python3:*)
---

Extract the Jira key from the user's argument if provided (e.g. `PANCCAED-1`), then run:

```bash
python3 .agents/scripts/create_branch.py [KEY]
```

Omit `[KEY]` if none was given — the script auto-detects from `.jira/`. Report the script's output verbatim.
