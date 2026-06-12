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
   - Si une **clé** est fournie ET que `.env` contient un `JIRA_API_TOKEN` non vide
     → **mode fetch** (étape 2a).
   - Sinon → **mode collage** (étape 2b).

2a. **Mode fetch.** Lis les variables Jira depuis `.env` (sans exécuter le fichier,
   qui contient aussi la config DB), puis récupère le récit dans `.jira/payload.json`.
   Cible **Jira Server/Data Center** : le Personal Access Token s'envoie en **Bearer**
   (pas de Basic auth `email:token` comme sur Cloud), API en **v2** :

   ```bash
   JIRA_BASE_URL=$(grep -E '^JIRA_BASE_URL=' .env | cut -d= -f2-)
   JIRA_API_TOKEN=$(grep -E '^JIRA_API_TOKEN=' .env | cut -d= -f2-)
   curl -sS -H "Authorization: Bearer $JIRA_API_TOKEN" \
     "$JIRA_BASE_URL/rest/api/2/issue/<KEY>?expand=names" \
     -o .jira/payload.json
   ```

   - `?expand=names` est important : il fournit la map `customfield_* → nom affiché`,
     qui sert à localiser Story Points et Sprint de façon fiable (étape 3).
   - Si le JSON renvoyé contient une clé `errorMessages` (token invalide, récit
     introuvable, droits manquants), montre l'erreur à l'utilisateur et arrête-toi.
   - Si `.env` est absent, copie-le depuis `.env.example` (`cp .env.example .env`).
   - Si `JIRA_API_TOKEN` est vide, indique à l'utilisateur de créer un Personal Access
     Token dans son profil Jira (`<JIRA_BASE_URL>/secure/ViewProfile.jspa` → onglet
     « Personal Access Tokens ») et de le coller dans `.env`, puis arrête-toi.

2b. **Mode collage.** Lis `.jira/payload.json`. S'il est absent, vide, ou contient
   encore `{}`, demande à l'utilisateur soit de fournir une clé (mode fetch), soit de
   coller le JSON du récit dans ce fichier, puis arrête-toi.

3. **Extraire les champs essentiels** depuis le JSON :
   - **key** → champ racine `key` (ex. `"PANACCESD-1"`). Sert à nommer le fichier de sortie.
   - **titre** → `fields.summary`
   - **type** → `fields.issuetype.name`
   - **description** → `fields.description`. C'est SOIT :
     - une **chaîne** (Jira Server/DC = **wiki markup**) → garde-la telle quelle, ou
       convertis le wiki markup en markdown : `h1.`/`h2.`→`#`/`##`, `*` puce→`-`,
       `# ` numéroté→`1.`, `[texte|url]`→`[texte](url)`, `{code}...{code}`→bloc \`\`\` ,
       `*gras*`/`_italique_` inchangés ;
     - un **objet ADF** (`{"type":"doc","content":[...]}`, Jira Cloud) → aplatis en
       markdown : `paragraph`→texte, `heading`→`#`, `bulletList`/`listItem`→`-`,
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
