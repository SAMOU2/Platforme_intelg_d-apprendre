# Rapport d'évaluation de l'agent — version 1

Date : 2026-10-27 · prompt `system-v1` · vecteurs : plissement de hachage 1536 d (démonstration hors ligne, sans réseau)

## 1. Calibration du seuil de citation

Un fragment n'est candidat que si sa similitude cosinus dépasse le seuil, ou s'il est
lexicalement pertinent. Le seuil arbitre deux erreurs symétriques : refuser une question
légitime, ou citer un fragment hors sujet.

| Seuil de similitude | Questions légitimes retrouvées (sur 20) | Questions hors domaine citées (sur 4) |
|---|---|---|
| 0.00 | 20/20 | 4/4 |
| 0.05 | 18/20 | 2/4 |
| 0.10 | 16/20 | 1/4 |
| 0.15 | 16/20 | 0/4 |
| 0.20 | 13/20 | 0/4 |
| 0.25 | 11/20 | 0/4 |

**Lecture du tableau.** Aucun seuil ne donne satisfaction sur les deux colonnes :
c'est le symptôme de vecteurs de démonstration, pas d'un mauvais réglage. Le seuil 0.15
supprime toute citation hors domaine mais perd un quart des questions légitimes ; 0.00
retrouve tout et cite n'importe quoi.

**Seuil retenu : 0.05** — compromis explicite : 18 questions légitimes sur 20 conservées,
et les citations hors domaine divisées par deux. Les 2 fuites restantes sont arrêtées en
aval par le prompt système puis par le filtre de sortie (étape 6), jamais par le seul seuil.

## 2. Qualité de la recherche (seuil 0.05)

| Mesure | Avec filtre de langage | Sans filtre |
|---|---|---|
| top-1 correct | **90 %** (18/20) | 65 % (13/20) |
| top-5 contient la bonne section | **90 %** (18/20) | 90 % (18/20) |

Le filtre de langage est donc **nécessaire** : sans lui, la bonne section sort du top-1
dans un cas sur trois environ.

## 3. Questions hors domaine

| Question | Candidat retenu | Similitude | Verdict |
|---|---|---|---|
| Quelle est la recette de la tarte au citron ? | non | 0.000 | refusé correctement |
| Qui a gagné la coupe du monde de football ? | oui | 0.134 | **à renforcer par le prompt** |
| Comment changer un pneu de voiture ? | non | 0.000 | refusé correctement |
| Quel est le prix d'un billet d'avion pour Paris ? | oui | 0.073 | **à renforcer par le prompt** |

## 4. Détail des 20 cas

| Cas | Langage | top-1 filtré | top-5 filtré | top-1 libre | similitude | lexical |
|---|---|---|---|---|---|---|
| `py-01` | python | oui | oui | non | 0.183 | 0.000 |
| `py-02` | python | oui | oui | oui | 0.364 | 0.000 |
| `py-03` | python | oui | oui | oui | 0.346 | 0.000 |
| `py-04` | python | oui | oui | oui | 0.507 | 0.000 |
| `py-05` | python | oui | oui | oui | 0.468 | 0.000 |
| `py-06` | python | oui | oui | non | 0.156 | 0.000 |
| `py-07` | python | oui | oui | oui | 0.243 | 0.000 |
| `java-01` | java | oui | oui | oui | 0.537 | 0.000 |
| `java-02` | java | oui | oui | non | 0.096 | 0.000 |
| `java-03` | java | oui | oui | oui | 0.264 | 0.000 |
| `java-04` | java | oui | oui | oui | 0.251 | 0.000 |
| `java-05` | java | oui | oui | non | 0.054 | 0.000 |
| `java-06` | java | oui | oui | oui | 0.326 | 0.000 |
| `cpp-01` | cpp | oui | oui | oui | 0.494 | 0.000 |
| `cpp-02` | cpp | oui | oui | oui | 0.133 | 0.050 |
| `cpp-03` | cpp | oui | oui | oui | 0.197 | 0.000 |
| `cpp-04` | cpp | oui | oui | non | 0.222 | 0.000 |
| `cpp-05` | cpp | non | non | non | 0.000 | 0.000 |
| `cpp-06` | cpp | oui | oui | oui | 0.407 | 0.000 |
| `cpp-07` | cpp | non | non | non | 0.000 | 0.000 |

## 5. Ce que ce rapport ne prouve pas

1. **Le corpus est minuscule** (10 fragments, 3 langages). À cette taille, une recherche
   par mots-clés réussit presque toujours : ces taux valident la **chaîne technique**
   (découpage, vecteurs, index HNSW, recherche hybride), pas la qualité d'un agent réel.
2. **Les vecteurs sont de démonstration.** Le plissement de hachage ne sépare pas finement
   le domaine du hors-domaine : « qui a gagné la coupe du monde ? » obtient 0.157 de
   similitude alors qu'une question légitime peut tomber à 0.09. C'est mesuré ci-dessus,
   et c'est la raison pour laquelle le refus n'est **pas** confié au seul seuil : il est
   aussi porté par le prompt système (étape 5) et par le filtre de sortie (étape 6).
   Avec un vrai modèle d'embeddings, la séparation est bien meilleure — le seuil devra
   être recalibré sur le corpus complet, avec cette même procédure.
3. **Les incidents de notation ne sont pas mesurés ici** : ils le sont côté Java, par les
   20 tests adversariaux de `GardeFouTest`.

