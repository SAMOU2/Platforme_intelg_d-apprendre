# Journal des versions du prompt

## system-v2 — 27/10/2026 (étape 5)

**Pourquoi** : la version 1 était un texte libre, sans format imposé, sans vocabulaire lié au
contrat et avec trois exemples seulement. Le modèle distant pouvait donc répondre hors format, et
l'application n'avait aucun moyen de distinguer une réponse valide d'un texte quelconque.

**Ce qui change**
- Le prompt est **généré** depuis `gabarit.md` et le contrat de refus : les huit libellés de la
  section 5 sont extraits du JSON, ils ne peuvent plus diverger de l'interface.
- Un **format de sortie** est imposé et versionné dans `sortie-schema.json` :
  `{version, reponse, citations[], notion, refus}`.
- Le champ `refus` **est** le motif (`AUCUN` = réponse normale) : un booléen et un motif pourraient
  se contredire, un seul champ rend la contradiction impossible.
- **Cinq exemples** few-shot, dont **deux refus** tirés du contrat — l'exigence du plan.
- Le rappel « le code vérifie avant et après » reste, pour que le modèle ne se croie pas seul
  garant des règles.

**Ce que cela ne change pas** : les interdits (ne note pas, ne valide pas, ne donne pas la
solution), le périmètre, la langue française, et le refus comme réponse de première classe.

## system-v1 — 26/10/2026

Première version : rôle, trois interdits, obligation de citer, trois exemples de refus. Suffisante
pour guider le modèle simulé, insuffisante pour contraindre un modèle distant.
