# Rapport de couverture du corpus — v1

*Généré le 2026-10-27T20:19:32 par `ia/corpus/construire.py`. Source : `ia/corpus/source-corpus.json` (e98930314f49af01…).*

## 1. Ce que contient le corpus

- **18 cours** (6 notions × 3 langages) ;
- **36 fragments citables** ;
- 10751 caractères, moyenne 299 par fragment (min 145, max 437).

| Langage | Fragments | Part |
|---|---:|---:|
| cpp | 12 | 33.3 % |
| java | 12 | 33.3 % |
| python | 12 | 33.3 % |

| Notion | Fragments |
|---|---:|
| BOUCLE — Boucles | 6 |
| COLL — Collections | 6 |
| COND — Conditions | 6 |
| ERREUR — Erreurs et exceptions | 6 |
| FONC — Fonctions et valeur de retour | 6 |
| VAR — Variables et types | 6 |

## 2. Équilibre entre les trois langages

**Le corpus est exactement équilibré** : chaque langage porte le même nombre de fragments, et chaque notion existe dans les trois langages. C'est la correction directe du déséquilibre relevé en v0.3.3 (5 fragments Python, 3 Java, 3 C++).

## 3. Passes de nettoyage

- rôles non `COURS` retirés : **0** ;
- blocs trop courts retirés (< 150 car. pour un TEXTE, < 40 pour un CODE) : **0** ;
- doublons exacts retirés : **0** ;
- quasi-doublons retirés (Jaccard ≥ 0.85) : **0**.

Les corrigés d'exercices notés n'entrent pas dans le corpus : l'agent cite un cours, il ne diffuse pas une correction. C'est le même principe que le contrat de refus (étape 1).

## 4. Jeu de 60 questions, figé avant tout réglage

- fichier : `ia/evaluation/jeu-de-test-v1-60.jsonl` ;
- **60 questions**, empreinte SHA-256 `a8f4fcc1dcfb1d0c7565bf7e258c7f0e…` ;
- répartition : 10 questions par notion (3 Python, 3 Java, 3 C++, 1 transversale).

| Notion | Python | Java | C++ | Transversale | Total |
|---|---:|---:|---:|---:|---:|
| VAR | 3 | 3 | 3 | 1 | 10 |
| COND | 3 | 3 | 3 | 1 | 10 |
| BOUCLE | 3 | 3 | 3 | 1 | 10 |
| FONC | 3 | 3 | 3 | 1 | 10 |
| COLL | 3 | 3 | 3 | 1 | 10 |
| ERREUR | 3 | 3 | 3 | 1 | 10 |

Chaque question porte un **témoin** (`attendu`) : le mot que doit contenir le fragment cité pour que la réponse soit juste. `construire.py` refuse de geler le jeu si un témoin est absent du cours attendu — même discipline que les tests du contrat de refus.

## 5. Où le corpus est consommé

| Consommateur | Fichier | Rôle |
|---|---|---|
| base PostgreSQL | `db/05-corpus-v1.sql` | insère les 18 cours / 36 blocs (idempotent), retire les cours de démonstration remplacés, rejoue l'extraction de `ia_fragments` |
| indexation | `ia/indexer.py` | découpe et vectorise les fragments de `ia_fragments` |
| évaluation | `ia/evaluer.py --jeu evaluation/jeu-de-test-v1-60.jsonl` | mesure top-1, top-5, témoin et hors-domaine |
| application JavaFX | `src/main/resources/fr/ihm2026/ia/corpus-fragments.tsv` | copie de `corpus-v1.tsv` : ce que `ModeleSimule` cite hors ligne |

## 6. Ce que ce rapport ne prouve pas

- Il ne mesure aucune performance par lui-même : les mesures de recherche sont dans `ia/evaluation/rapport-v1-60.md`, produit **après** le gel du jeu, et elles ne peuvent plus servir à ajuster les questions.
- Les vecteurs de démonstration (plissement de hachage) restent inchangés : ils valident la chaîne, pas la sémantique.
- L'équilibre porte sur le **nombre** de fragments, pas sur leur difficulté : la répartition par niveau reste à valider par l'enseignant — tous les cours sont rattachés au rang 1 de la base de démonstration.
- Le corpus sert un modèle **simulé** : `ModeleSimule` cite, il ne rédige pas.
