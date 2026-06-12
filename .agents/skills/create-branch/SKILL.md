---
name: create-branch
description: Crée/bascule sur la branche d'un récit Jira (ex. PANACCESD-1) depuis le HEAD courant, en conservant les modifications non commitées. Use at the start of work on a Jira story. Ne commit rien.
---

# Create branch

Crée la branche au nom du récit Jira et bascule dessus. Action ponctuelle, en début de
travail. **Ne commit rien** (voir le skill `commit-changes` pour ça).

## Étapes

1. **Déterminer la clé du récit**, dans cet ordre :
   - argument fourni au skill (ex. `PANACCESD-1`) ;
   - sinon, le champ `key` de `.jira/payload.json` s'il existe ;
   - sinon, s'il existe exactement un fichier `.jira/<KEY>.md`, déduis la clé du nom ;
   - sinon, demande la clé à l'utilisateur et arrête-toi s'il n'en fournit pas.

   La clé doit matcher `^[A-Z][A-Z0-9]+-\d+$`. Sinon, demande confirmation.

2. **Créer / basculer sur la branche `<KEY>`** :
   - branche courante déjà = `<KEY>` → ne rien faire, le signaler ;
   - la branche existe déjà → `git switch <KEY>` ;
   - sinon → `git switch -c <KEY>` (crée depuis le HEAD courant ; les modifications
     non commitées sont conservées et suivent sur la nouvelle branche).

3. **Confirmer** : la branche active, et si des modifications non commitées ont suivi.
