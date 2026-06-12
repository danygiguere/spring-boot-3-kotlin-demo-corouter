---
name: create-pr
description: Crée une pull request GitHub via l'API REST (curl + token, sans gh). Génère titre + body depuis les commits de la branche, LES MONTRE pour approbation, puis push et crée le PR. Use when the user wants to open a PR for the current branch.
---

# Create PR

Ouvre une pull request GitHub pour la branche courante, sans `gh`, via l'API REST.
**Ne pushe jamais** — c'est au dev de pousser sa branche ; le skill exige qu'elle soit
déjà sur le remote. **Étape d'approbation obligatoire** : montre le titre et le body,
attends le « go » de l'utilisateur avant de créer le PR.

## Pré-requis

1. **Token** : lis `GITHUB_TOKEN` depuis `.env`
   (`GITHUB_TOKEN=$(grep -E '^GITHUB_TOKEN=' .env | cut -d= -f2-)`). S'il est absent/vide,
   indique à l'utilisateur de créer un token (GitHub → Settings → Developer settings →
   Personal access tokens, fine-grained, permission *Pull requests: Read and write*) et de
   le coller dans `.env`, puis arrête-toi. Si `.env` n'existe pas, `cp .env.example .env`.
2. **Owner/Repo** : déduis-les de `git remote get-url origin` (formes
   `git@github.com:OWNER/REPO.git` ou `https://github.com/OWNER/REPO.git`).
3. **Branche** : `git branch --show-current`. Si c'est `main` (ou la branche par
   défaut), arrête-toi — il faut être sur une branche de travail.
4. **Base** : `main` par défaut.
5. **Branche déjà poussée** : `git ls-remote --heads origin <branche>` doit renvoyer une
   ligne. Si c'est vide → **arrête-toi** et demande au dev de pousser d'abord
   (`git push -u origin <branche>`). Le skill ne pousse jamais lui-même.

## Étapes

1. **Rassembler le contexte** de la branche vs `main` :
   - `git log --oneline main..HEAD` — commits à inclure ;
   - `git diff --stat main...HEAD` — fichiers touchés.
   - Si 0 commit d'écart → dis-le et arrête-toi (rien à proposer).

2. **Générer le titre et le body** :
   - **clé** : déduite de la branche courante si elle matche `^[A-Z][A-Z0-9]+-\d+$`.
   - **titre** : concis, impératif, sans la clé Jira (la branche la porte). S'il n'y a
     qu'un commit, pars de son sujet ; sinon synthétise l'ensemble.
   - Lis `JIRA_BASE_URL` depuis `.env`
     (`JIRA_BASE_URL=$(grep -E '^JIRA_BASE_URL=' .env | cut -d= -f2-)`) pour le lien.
   - **body** : structure markdown en trois sections :
     ```
     ## Récit (requis)
     <le contenu pertinent de .jira/<KEY>.md : description / critères d'acceptation
      du récit. Tu peux inclure le tableau Type / Story Points / Sprint. Si le .md
      n'existe pas, résume brièvement l'objectif en 1-3 phrases.>

     ## Lien Jira
     [<KEY>](<JIRA_BASE_URL>/browse/<KEY>)
     <si JIRA_BASE_URL est vide/absent, mets juste la clé <KEY> sans lien>

     ## Ce qui a été fait
     - <point concret issu des commits / du diff — ce que CE travail change>
     - ...

     ## Tests
     - `<chemin du fichier de test>` — <fonction de test ajoutée et ce qu'elle vérifie>
     - ...
     <si rien n'a changé sous src/test/ : écris exactement "Aucun test ajouté.">
     ```
   - La section « Récit » décrit l'**intention** (depuis le .md) ; « Ce qui a été fait »
     décrit l'**implémentation** (depuis les commits/diff). Ne confonds pas les deux.
   - Pour la section « Tests » : repère dans le diff (`git diff main...HEAD`) les fichiers
     sous `src/test/` et les **fonctions de test ajoutées** (annotées `@Test`, ou les
     `fun ...()` d'une classe de test). Liste chaque test avec son fichier et, en une
     ligne, ce qu'il couvre. Si aucun fichier `src/test/` n'a changé, écris « Aucun test
     ajouté. » — n'invente jamais de tests.

3. **APPROBATION** — montre à l'utilisateur, dans des blocs distincts :
   - le **titre** proposé ;
   - le **body** proposé ;
   - la cible : `OWNER/REPO`, `head=<branche>` → `base=main`.
   Demande explicitement son accord. S'il veut des modifs, applique-les et re-montre.
   **N'enchaîne pas** sur la création du PR sans un « go » clair.

4. **Après approbation seulement** (la branche est déjà sur le remote — cf. pré-requis 5 ;
   le skill ne pousse pas) :
   a. Crée le PR. Construis le JSON proprement (le body est multi-ligne) — écris la
      charge utile dans un fichier temporaire pour éviter les soucis d'échappement :
      ```bash
      GITHUB_TOKEN=$(grep -E '^GITHUB_TOKEN=' .env | cut -d= -f2-)
      jq -n --arg t "<titre>" --arg b "<body>" --arg h "<branche>" \
        '{title:$t, body:$b, head:$h, base:"main"}' > .git/pr-payload.json
      curl -sS -X POST \
        -H "Authorization: Bearer $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github+json" \
        "https://api.github.com/repos/OWNER/REPO/pulls" \
        -d @.git/pr-payload.json
      ```
      (Si `jq` n'est pas dispo, échappe le JSON à la main avec soin.)
   b. Nettoie : `rm -f .git/pr-payload.json`.

5. **Résultat** :
   - succès → extrais `html_url` de la réponse et donne le lien du PR à l'utilisateur ;
   - erreur (`errorMessages`, « A pull request already exists », 401/403) → montre le
     message tel quel et n'essaie pas de contourner.

   **Ne merge jamais** le PR. Laisse l'utilisateur décider.
