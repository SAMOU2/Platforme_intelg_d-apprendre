# Modèle local — mesures

Généré par `ia/modele/entrainer.py`. Ne pas éditer à la main : régénérer.

| | |
|---|---|
| Modèle | `modele-local-v2` · entraîné le 2026-10-28 |
| Vocabulaire | 262 termes |
| Données d'entraînement | 60 questions du jeu gelé + 48 questions synthétiques annotées + 64 négatifs et exemples du contrat de refus |
| Empreintes | jeu `a8f4fcc1dcfb1d0c` · corpus `4649e72f474b49d0` · négatifs `143494406ee42e7c` · contrat `a7a6f88cdccc12e7` · synthèse `795690767d3589c8` · test `1eb62bc06cde632c` |

## Tête « notion » — 6 classes

Validation croisée laissée-un-dehors : **90/108 = 83 %**.
α = 1.0 (grille : 0.05 → 81 %, 0.1 → 81 %, 0.3 → 81 %, 1.0 → 83 %, 3.0 → 83 %).

Confusions hors diagonale : `BOUCLE→COLL` ×2, `BOUCLE→COND` ×1, `COLL→BOUCLE` ×2, `COLL→FONC` ×1, `COND→COLL` ×1, `COND→ERREUR` ×1, `COND→FONC` ×1, `ERREUR→COLL` ×1, `ERREUR→FONC` ×2, `FONC→COLL` ×1, `VAR→BOUCLE` ×1, `VAR→COLL` ×1, `VAR→COND` ×1, `VAR→FONC` ×2.

Évaluation indépendante sur les questions synthétiques réservées (jamais utilisées à l'entraînement ni au réglage) :
**14/24 = 58 %**.
Ce jeu est synthétique et de petite taille ; il sert de contrôle initial, pas de mesure de performance sur de vrais apprenants.

## Rangement du fragment, et témoin attendu

| Rangement | Section juste | Témoin attendu présent dans le fragment choisi |
|---|---|---|
| Lexical (sans apprentissage) | **50/60 = 83 %** | — |
| Modèle local (notion apprise) | **60/60 = 100 %** (appris sur ces questions) | **52/60 = 87 %** |

La colonne « modèle local » est **en apprentissage** : ces questions ont servi à l'entraîner, c'est donc
une borne haute. Le chiffre honnête de la tête notion est la validation croisée ci-dessus
(83 %). Le rangement lexical, lui, n'apprend rien : ses 83 %
sont comparables à ceux de l'étape 2 — même protocole, autre implémentation.

## Tête « décision » — répondre ou refuser

Validation croisée laissée-un-dehors : **135/182 = 74 %**
(α = 0.05).

Mesure sur les négatifs **de mesure**, jamais vus à l'entraînement :

| | Résultat |
|---|---|
| Demandes de refus détectées comme refus | **23/24 = 96 %** |
| Questions légitimes refusées à tort | **9/40 = 22 %** |
| Seuil de couverture | 0.1 (part pondérée des mots de la question connus du modèle) |

Détail des réponses (motif attendu → classe prédite) : `DECISION_DEMANDEE→DECISION_DEMANDEE` ×5 · `DECISION_DEMANDEE→SOLUTION_DEMANDEE` ×1 · `HORS_PERIMETRE→AUCUN` ×1 · `HORS_PERIMETRE→DECISION_DEMANDEE` ×8 · `HORS_PERIMETRE→HORS_PERIMETRE` ×4 · `HORS_PERIMETRE→SOLUTION_DEMANDEE` ×1 · `SOLUTION_DEMANDEE→SOLUTION_DEMANDEE` ×4.

## Ce que le modèle ne fait pas

1. **Il ne rédige pas.** La réponse reste extractive : elle cite une phrase du cours retrouvé. Un
   modèle génératif local (LLM) n'est pas embarqué — la décision de l'étape 4 (RAG sur API) le
   réservait au fournisseur distant ; ici, l'apprentissage porte sur le **classement** et la
   **décision**, les deux endroits où un petit modèle supervisé est meilleur qu'une règle écrite.
2. **Il ne couvre pas les refus contextuels.** `TEST_EN_COURS`, `QUOTA`, `INDISPONIBLE`,
   `PUBLICATION_AUTOMATIQUE` et `DONNEES_INSUFFISANTES` dépendent de l'état de la session, pas des
   mots de la question : ce sont les garde-fous et la politique de repli qui les décident, pas ce
   modèle.
3. **Il ne note pas**, ne valide pas, ne débloque pas — le contrat de l'étape 1 reste la loi.
