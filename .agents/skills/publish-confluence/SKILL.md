---
name: publish-confluence
description: Publie le travail de la branche courante vers une page Confluence. Échoue si Étiquettes est N/A. Génère le contenu depuis .jira/<KEY>.md + commits, écrit un fichier temporaire .jira/<KEY>_confluence.md, puis publie via l'API (Bearer token, aucun mot de passe). Use after /get-jira-story to document a story in Confluence.
allowed-tools: Bash(python3:*), Write
---

# Publish to Confluence

**Requires:** `.jira/<KEY>.md` exists (run `/get-jira-story` first). Étiquettes must not be N/A — the preflight fails otherwise.

## Steps

1. Run the preflight — validates Étiquettes and gathers all context in one call:
   ```bash
   python3 .agents/scripts/preflight_confluence.py
   ```
   Stop and report any line starting with `ERROR`.

2. From the preflight output, generate a markdown document and write it to `.jira/<KEY>_confluence.md`:

   ```markdown
   # <KEY> — <titre from JIRA STORY section>

   <full verbatim content of the JIRA STORY section — table + description + acceptance criteria>

   ## Ce qui a été fait
   - <concrete point from COMMITS / FILES CHANGED — what this work actually changes>
   - ...

   ## Tests
   - `path/to/Test.kt` — <what the added @Test covers>
   <if TEST DIFF shows no changes: write exactly "Aucun test ajouté.">
   ```

   Use the exact KEY from `=== KEY ===`. The first line must be `# KEY — titre` — the script uses it as the Confluence page title.

3. Run the publish script:
   ```bash
   python3 .agents/scripts/publish_to_confluence.py <KEY> .jira/<KEY>_confluence.md
   ```
   Report any ERROR lines and stop. On success, report the Confluence page URL printed by the script.

**`.jira/` is gitignored — the temp file is never committed.**
