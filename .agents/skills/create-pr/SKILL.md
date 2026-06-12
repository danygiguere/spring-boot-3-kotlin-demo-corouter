---
name: create-pr
description: Prépare une pull request GitHub via une URL "compare" pré-remplie (sans token, sans gh). Génère titre + body depuis les commits de la branche, LES MONTRE pour approbation, puis donne l'URL à ouvrir — le dev clique "Create". Use when the user wants to open a PR for the current branch.
---

# Create PR

Prépare une pull request GitHub pour la branche courante, **sans `gh` et sans token** :
le skill construit une URL « compare » pré-remplie (titre + body) que le dev ouvre dans le
navigateur pour cliquer « Create ». **Ne pushe pas** — la branche doit déjà être sur le
remote. **Étape d'approbation obligatoire** : montre le titre et le body, attends le
« go » avant de générer l'URL.

## Pré-requis

1. **Owner/Repo** : déduis-les de `git remote get-url origin` (formes
   `git@github.com:OWNER/REPO.git` ou `https://github.com/OWNER/REPO.git`).
2. **Branche** : `git branch --show-current`. Si c'est `main` (ou la branche par
   défaut), arrête-toi — il faut être sur une branche de travail.
3. **Base** : `main` par défaut.
4. **Branche déjà poussée** : `git ls-remote --heads origin <branche>` doit renvoyer une
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

4. **Après approbation seulement** (la branche est déjà sur le remote — cf. pré-requis 4) :
   construis l'**URL « compare » pré-remplie**. Le titre et le body doivent être
   **URL-encodés** (le body est multi-ligne) — utilise `jq @uri` :
   ```bash
   ENC_T=$(jq -rn --arg x "<titre>" '$x|@uri')
   ENC_B=$(jq -rn --arg x "<body>"  '$x|@uri')
   echo "https://github.com/OWNER/REPO/compare/main...<branche>?expand=1&title=$ENC_T&body=$ENC_B"
   ```
   (Sans `jq` : `python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))' "<texte>"`.)

5. **Résultat** : donne l'URL à l'utilisateur en lui disant d'**ouvrir le lien** → le
   formulaire GitHub s'affiche titre + body pré-remplis → il clique **« Create pull
   request »**. Le skill **ne crée pas** le PR lui-même et **ne merge jamais**.

   ⚠️ Si le body est très long, l'URL peut dépasser la limite du navigateur (~8 ko) et
   être tronquée. Dans ce cas, donne l'URL avec le **titre seul** et fournis le **body à
   part** pour qu'il le colle à la main dans le formulaire.
