---
name: get-jira-story
description: Récupère un récit Jira et le convertit en fiche markdown .jira/<KEY>.md pour servir de contexte de développement. Donne une clé (ex. PANCCAED-1) et le skill fetch le récit via l'API (token local, aucun mot de passe), ou lit un JSON déjà collé dans .jira/payload.json. Le dossier .jira/ est gitignored.
allowed-tools: Bash(python3:*)
---

Extract the Jira key from the user's argument if provided (e.g. `PANCCAED-1`), then run:

```bash
python3 .agents/scripts/get_jira_story.py [KEY]
```

Omit `[KEY]` if none was given — the script falls back to `.jira/payload.json`. Report the script's output verbatim.
