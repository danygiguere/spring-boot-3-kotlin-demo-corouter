---
name: jira-story
description: Récupère un récit Jira et le convertit en fiche markdown .jira/<KEY>.md pour servir de contexte de développement. Donne une clé (ex. PANACCESD-1) et le skill fetch le récit via l'API (token local, aucun mot de passe), ou lit un JSON déjà collé dans .jira/payload.json. Le dossier .jira/ est gitignored.
---

# Jira Story → Markdown

Transforme un récit Jira en une fiche markdown propre, locale et gitignorée.

## Deux modes

- **Fetch (préféré)** — l'utilisateur fournit une clé (ex. `PANACCESD-1`, en argument
  du skill ou demandée). Le skill récupère le récit via l'API Jira avec le token local.
- **Collage (fallback)** — pas de clé / pas de token : le skill lit le JSON déjà collé
  dans `.jira/payload.json`.

## Étapes

1. **Déterminer le mode.**
   - Si une **clé** est fournie ET que `.jira/config.env` existe avec un `JIRA_API_TOKEN`
     non vide → **mode fetch** (étape 2a).
   - Sinon → **mode collage** (étape 2b).

2a. **Mode fetch.** Lis `.jira/config.env` (`JIRA_BASE_URL`, `JIRA_EMAIL`, `JIRA_API_TOKEN`)
   et récupère le récit, en écrivant le résultat dans `.jira/payload.json` :

   ```bash
   set -a; . .jira/config.env; set +a
   curl -sS -u "$JIRA_EMAIL:$JIRA_API_TOKEN" \
     "$JIRA_BASE_URL/rest/api/latest/issue/<KEY>?expand=names" \
     -o .jira/payload.json
   ```

   - `?expand=names` est important : il fournit la map `customfield_* → nom affiché`,
     qui sert à localiser Story Points et Sprint de façon fiable (étape 3).
   - Si le JSON renvoyé contient une clé `errorMessages` (token invalide, récit
     introuvable, droits manquants), montre l'erreur à l'utilisateur et arrête-toi.
   - Si `JIRA_API_TOKEN` est vide, indique à l'utilisateur de créer un token
     (https://id.atlassian.com/manage-profile/security/api-tokens) et de le coller
     dans `.jira/config.env`, puis arrête-toi.

2b. **Mode collage.** Lis `.jira/payload.json`. S'il est absent, vide, ou contient
   encore `{}`, demande à l'utilisateur soit de fournir une clé (mode fetch), soit de
   coller le JSON du récit dans ce fichier, puis arrête-toi.

3. **Extraire les champs essentiels** depuis le JSON :
   - **key** → champ racine `key` (ex. `"PANACCESD-1"`). Sert à nommer le fichier de sortie.
   - **titre** → `fields.summary`
   - **type** → `fields.issuetype.name`
   - **description** → `fields.description`. C'est SOIT :
     - une **chaîne** (API en texte) → utilise telle quelle ;
     - un **objet ADF** (`{"type":"doc","content":[...]}`) → aplatis en markdown :
       `paragraph`→texte, `heading`→`#`, `bulletList`/`listItem`→`-`,
       `orderedList`→`1.`, `codeBlock`→bloc \`\`\` , `link`→`[texte](href)`,
       marks `strong`/`em`→`**`/`*`. Préserve une structure lisible.
   - **story points** → si le JSON contient `names`, trouve le `customfield_*` dont le
     nom matche `/story point/i` et lis sa valeur (un nombre). Sinon, repère un
     `customfield_*` à valeur numérique seule. Si introuvable ou null → `N/A`.
   - **sprint** → via `names`, le `customfield_*` dont le nom matche `/^sprint/i` : c'est
     un tableau d'objets sprint (`name`/`state`/`boardId`). Prends le `name` du sprint
     actif (`state == "active"`), sinon le dernier. Si absent → `N/A`.

   ⚠️ N'invente jamais une valeur : en cas d'ambiguïté, mets `N/A`.

4. **Écrire `.jira/<KEY>.md`** avec ce gabarit :

   ```markdown
   # <KEY> — <titre>

   | Champ | Valeur |
   |---|---|
   | Type | <type> |
   | Story Points | <points> |
   | Sprint | <sprint> |

   ## Description

   <description en markdown>
   ```

5. **Garantir l'exclusion git.** Si le `.gitignore` du projet ne couvre pas déjà `.jira/`,
   ajoute-la. Rien sous `.jira/` ne doit jamais être commité (ni le token, ni les payloads).

6. **Confirmer** : chemin du fichier écrit (`.jira/<KEY>.md`) et champs laissés à `N/A`.
