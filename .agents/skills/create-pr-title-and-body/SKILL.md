---
name: create-pr-title-and-body
description: Génère uniquement le titre et le body d'une pull request (texte à copier-coller, sans URL, sans gh, sans token) à partir des commits ET des changements non commités de la branche courante, comparés à une branche de base choisie par le dev. Use when the user wants the PR title and body for the current branch.
allowed-tools: Bash(python3:*)
---

# Create PR title and body

**Only produce the title and the body as text.** No GitHub URL, no push, no PR creation.

## Steps

1. Ask the user which branch is the PR **base** (what to compare against, e.g. `main`
   or `master`). Run the preflight with no argument first to get the detected default:
   ```bash
   python3 .agents/scripts/preflight_pr.py
   ```
   The first line is `=== DEFAULT BASE ===` with the detected default — suggest it.
   Once the user picks a base, re-run with it:
   ```bash
   python3 .agents/scripts/preflight_pr.py <base>
   ```
   Stop and report any line starting with `ERROR`.

2. From the output, generate:
   - **Title**: `KEY - <concise imperative summary>`, where KEY is the value of the
     `=== JIRA KEY ===` section of the preflight (e.g.
     `PANCCAED-1 - Implémenter l'authentification JWT`). If that section is `(none)`,
     omit the prefix. Single commit → derive the summary from its subject. Multiple →
     synthesise. The diff also includes uncommitted changes, so reflect those too.
   - **Body** with this exact structure (the collapsible block needs the blank lines
     shown, or GitHub won't render the markdown inside it):
     ```
     <details>
     <summary>Requis de récit</summary>

     <copy here, character-for-character, ONLY the `| Champ | Valeur |` metadata table
      from the `=== JIRA STORY (KEY) ===` section of the preflight output (KEY comes from
      .jira/payload.json, not necessarily the branch name). Just that table — do NOT
      include the title line, description, or acceptance criteria, and do not paraphrase.
      If the preflight printed no JIRA STORY section at all, omit this <details> block.>

     </details>

     ## Lien Jira
     [KEY](JIRA_BASE_URL/browse/KEY)
     <if JIRA_BASE_URL is empty/absent, just write the KEY with no link>

     ## Ce qui a été fait
     - <concrete point derived from commits + uncommitted diff — what THIS work changes>
     - ...

     ## Tests
     - `path/to/Test.kt` — <what the added @Test function verifies>
     <if TEST DIFF section shows no changes: write exactly "Aucun test ajouté.">
     ```

3. Show the proposed **title** and **body** in two separate copyable blocks, with the
   compared range (`<base>...current branch`). That is the final output — nothing else
   to run.
