---
name: create-commit
description: Examine le diff courant, génère un message de commit au style du repo, puis commit (l'invocation du skill EST la demande explicite). Ne push jamais — c'est au dev de pusher. Use when the user wants to commit their current changes. Run as many times as needed during a story.
---

# Create commit

Examine les modifications courantes, rédige un message au style du repo, et committe.
Invoquer ce skill vaut demande explicite de commit (cf. AGENTS.md). **Ne push jamais.**

## Étapes

1. **Examiner le diff** :
   - `git status --short` pour la vue d'ensemble ;
   - `git diff HEAD` (stagé + non stagé vs dernier commit) pour le contenu.
   - S'il n'y a **aucune** modification, dis-le et arrête-toi (rien à committer).

2. **Comprendre l'intention** (optionnel) : si un `.jira/<KEY>.md` existe (clé déduite de
   la branche courante si elle matche `^[A-Z][A-Z0-9]+-\d+$`), lis-le pour le contexte.

3. **Rédiger le message** au style du repo (vérifie via `git log --oneline -15`) :
   - **sujet** à l'impératif, capitalisé, ≤ ~50 caractères, **sans** point final,
     **sans** préfixe conventional (`feat:` etc.), **sans** la clé Jira (la branche la
     porte déjà → Jira lie automatiquement) ;
   - décris ce que fait *réellement* le diff, pas un titre de récit recopié ;
   - si le diff couvre plusieurs aspects, ajoute un **corps** en bullets (`- ...`)
     après une ligne vide.

4. **Montrer le message** proposé à l'utilisateur dans un bloc, puis **committer** :
   ```bash
   git add -A
   git commit -m "<sujet>" [-m "<corps>"]
   ```
   - `git add -A` stage tout le travail courant (`.jira/` est gitignored, donc exclu).
   - Si l'utilisateur a demandé de ne stager qu'une partie, respecte-le.

5. **Ne pas push.** Confirme le hash + sujet du commit créé et rappelle que le push
   reste à faire manuellement.
