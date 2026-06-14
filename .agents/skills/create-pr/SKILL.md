---
name: create-pr
description: Prépare une pull request GitHub via une URL "compare" pré-remplie (sans token, sans gh). Génère titre + body depuis les commits de la branche, LES MONTRE pour approbation, puis donne l'URL à ouvrir — le dev clique "Create". Use when the user wants to open a PR for the current branch.
allowed-tools: Bash(python3:*), Bash(jq:*)
---

# Create PR

**Never push. Never create the PR.** The user opens the URL and clicks "Create pull request".

## Steps

1. Run the preflight script (one call replaces all git/env lookups):
   ```bash
   python3 .agents/scripts/preflight_pr.py
   ```
   Stop and report any line starting with `ERROR`.

2. From the output, generate:
   - **Title**: concise, imperative, no Jira key in the title (the branch carries it).
     Single commit → derive from its subject. Multiple → synthesise.
   - **Body** with this exact structure:
     ```
     ## Récit
     <from JIRA STORY section — description + acceptance criteria.
      Include the Type/Story Points/Sprint table if present.
      If no JIRA STORY section: 1-3 sentence summary of the branch's goal.>

     ## Lien Jira
     [KEY](JIRA_BASE_URL/browse/KEY)
     <if JIRA_BASE_URL is empty/absent, just write the KEY with no link>

     ## Ce qui a été fait
     - <concrete point derived from commits/diff — what THIS work changes>
     - ...

     ## Tests
     - `path/to/Test.kt` — <what the added @Test function verifies>
     <if TEST DIFF section shows no changes: write exactly "Aucun test ajouté.">
     ```

3. **APPROVAL** — show in separate blocks:
   - the proposed title
   - the proposed body
   - the target: `OWNER/REPO`, `head=BRANCH` → `base=main`

   Ask for explicit approval. Apply requested changes and re-show. **Do not proceed without a clear go.**

4. After approval, build the prefilled URL:
   ```bash
   ENC_T=$(jq -rn --arg x "TITLE" '$x|@uri')
   ENC_B=$(jq -rn --arg x "BODY"  '$x|@uri')
   echo "https://github.com/OWNER/REPO/compare/main...BRANCH?expand=1&title=$ENC_T&body=$ENC_B"
   ```
   If `jq` is unavailable: `python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))' "TEXT"`

5. Give the user the URL to open. If the URL exceeds ~8 KB, provide the title-only URL and the body separately to paste by hand.
