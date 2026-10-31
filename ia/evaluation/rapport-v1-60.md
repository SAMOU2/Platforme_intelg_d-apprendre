# Rapport d'évaluation de l'agent — v1 (corpus-v1)

Date : 2026-10-27 · prompt `system-v1` · jeu de test : `jeu-de-test-v1-60.jsonl` (60 questions) · vecteurs : plissement de hachage 1536 d (démonstration hors ligne, sans réseau)

## 1. Calibration du seuil de citation

Un fragment n'est candidat que si sa similitude cosinus dépasse le seuil, ou s'il est
lexicalement pertinent. Le seuil arbitre deux erreurs symétriques : refuser une question
légitime, ou citer un fragment hors sujet.

| Seuil de similitude | Questions légitimes retrouvées (sur 60) | Questions hors domaine citées (sur 4) |
|---|---|---|
| 0.00 | 60/60 | 4/4 |
| 0.05 | 58/60 | 2/4 |
| 0.10 | 54/60 | 1/4 |
| 0.15 | 45/60 | 0/4 |
| 0.20 | 39/60 | 0/4 |
| 0.25 | 25/60 | 0/4 |

**Lecture du tableau.** Aucun seuil ne donne satisfaction sur les deux colonnes :
c'est le symptôme de vecteurs de démonstration, pas d'un mauvais réglage. Plus le seuil
monte, moins l'agent cite hors domaine, mais plus il refuse des questions légitimes.

**Seuil retenu : 0.05** — compromis explicite : 58 questions légitimes sur 60 conservées, et les citations hors domaine ramenées à 2/4. Les fuites restantes sont arrêtées
en aval par le prompt système puis par le filtre de sortie (étape 6), jamais par le
seuil seul.

## 2. Qualité de la recherche (seuil 0.05)

| Mesure | Avec filtre de langage | Sans filtre |
|---|---|---|
| top-1 correct | **73 %** (44/60) | 63 % (38/60) |
| top-5 contient la bonne section | **92 %** (55/60) | 85 % (51/60) |

Le filtre de langage apporte **10 points** de top-1 : il reste donc nécessaire,
mais il ne suffit pas — un fragment de la bonne section peut précéder le fragment qui
contient réellement la réponse.

## 3. Réponse juste : le témoin attendu est-il cité ?

Chaque question du jeu gelé porte un **témoin** (`attendu`) : le mot que doit contenir le fragment cité. Mesure sur 60 questions à témoin.

| Mesure | Résultat |
|---|---|
| témoin présent dans le top-1 | **77 %** (46/60) |
| témoin présent dans le top-5 | **87 %** (52/60) |

C'est la mesure la plus sévère : elle porte sur le **contenu** cité et non sur la section. Un écart avec le top-1 ci-dessus
signifie que la bonne section est trouvée mais que le fragment utile n'est pas le premier.

## 4. Questions hors domaine

| Question | Candidat retenu | Similitude | Verdict |
|---|---|---|---|
| Quelle est la recette de la tarte au citron ? | non | 0.000 | refusé correctement |
| Qui a gagné la coupe du monde de football ? | oui | 0.133 | **à renforcer par le prompt** |
| Comment changer un pneu de voiture ? | non | 0.000 | refusé correctement |
| Quel est le prix d'un billet d'avion pour Paris ? | oui | 0.077 | **à renforcer par le prompt** |

## 5. Détail des 60 cas

| Cas | Langage | top-1 filtré | top-5 filtré | top-1 libre | témoin top-1 | similitude | lexical |
|---|---|---|---|---|---|---|---|
| `VAR-p1` | python | oui | oui | oui | oui | 0.226 | 0.000 |
| `VAR-p2` | python | oui | oui | non | oui | 0.094 | 0.000 |
| `VAR-p3` | python | non | oui | non | oui | 0.122 | 0.000 |
| `VAR-j1` | java | oui | oui | oui | oui | 0.128 | 0.000 |
| `VAR-j2` | java | oui | oui | oui | oui | 0.207 | 0.000 |
| `VAR-j3` | java | non | oui | non | non | 0.106 | 0.000 |
| `VAR-c1` | cpp | non | oui | non | non | 0.125 | 0.000 |
| `VAR-c2` | cpp | non | oui | non | oui | 0.314 | 0.000 |
| `VAR-c3` | cpp | non | oui | non | non | 0.217 | 0.000 |
| `VAR-t1` | tous | non | non | non | oui | 0.251 | 0.000 |
| `COND-p1` | python | non | non | non | non | 0.134 | 0.000 |
| `COND-p2` | python | oui | oui | oui | oui | 0.202 | 0.000 |
| `COND-p3` | python | oui | oui | oui | oui | 0.220 | 0.000 |
| `COND-j1` | java | oui | oui | oui | oui | 0.337 | 0.000 |
| `COND-j2` | java | oui | oui | oui | oui | 0.256 | 0.000 |
| `COND-j3` | java | oui | oui | oui | oui | 0.290 | 0.000 |
| `COND-c1` | cpp | oui | oui | oui | oui | 0.429 | 0.000 |
| `COND-c2` | cpp | oui | oui | oui | oui | 0.193 | 0.000 |
| `COND-c3` | cpp | non | non | non | non | 0.000 | 0.000 |
| `COND-t1` | tous | non | oui | non | oui | 0.282 | 0.000 |
| `BOUCLE-p1` | python | non | oui | non | oui | 0.129 | 0.000 |
| `BOUCLE-p2` | python | oui | oui | oui | oui | 0.143 | 0.000 |
| `BOUCLE-p3` | python | oui | oui | oui | oui | 0.166 | 0.000 |
| `BOUCLE-j1` | java | oui | oui | oui | oui | 0.289 | 0.000 |
| `BOUCLE-j2` | java | oui | oui | oui | oui | 0.249 | 0.000 |
| `BOUCLE-j3` | java | oui | oui | non | oui | 0.265 | 0.000 |
| `BOUCLE-c1` | cpp | oui | oui | oui | oui | 0.696 | 0.000 |
| `BOUCLE-c2` | cpp | oui | oui | oui | non | 0.454 | 0.000 |
| `BOUCLE-c3` | cpp | non | oui | non | non | 0.167 | 0.000 |
| `BOUCLE-t1` | tous | oui | oui | oui | oui | 0.311 | 0.000 |
| `FONC-p1` | python | oui | oui | oui | oui | 0.292 | 0.000 |
| `FONC-p2` | python | oui | oui | oui | oui | 0.415 | 0.005 |
| `FONC-p3` | python | oui | oui | oui | oui | 0.247 | 0.000 |
| `FONC-j1` | java | oui | oui | oui | oui | 0.235 | 0.000 |
| `FONC-j2` | java | oui | oui | oui | oui | 0.303 | 0.000 |
| `FONC-j3` | java | oui | oui | non | non | 0.071 | 0.000 |
| `FONC-c1` | cpp | oui | oui | oui | oui | 0.247 | 0.000 |
| `FONC-c2` | cpp | oui | oui | oui | oui | 0.229 | 0.000 |
| `FONC-c3` | cpp | oui | oui | non | oui | 0.091 | 0.000 |
| `FONC-t1` | tous | oui | oui | oui | oui | 0.386 | 0.000 |
| `COLL-p1` | python | oui | oui | non | oui | 0.189 | 0.000 |
| `COLL-p2` | python | non | non | non | non | 0.080 | 0.000 |
| `COLL-p3` | python | oui | oui | oui | oui | 0.322 | 0.000 |
| `COLL-j1` | java | oui | oui | oui | oui | 0.258 | 0.000 |
| `COLL-j2` | java | oui | oui | oui | oui | 0.206 | 0.000 |
| `COLL-j3` | java | non | oui | non | non | 0.105 | 0.000 |
| `COLL-c1` | cpp | oui | oui | non | non | 0.111 | 0.000 |
| `COLL-c2` | cpp | oui | oui | oui | oui | 0.194 | 0.000 |
| `COLL-c3` | cpp | non | non | non | non | 0.000 | 0.000 |
| `COLL-t1` | tous | non | oui | non | non | 0.222 | 0.000 |
| `ERREUR-p1` | python | oui | oui | oui | oui | 0.218 | 0.000 |
| `ERREUR-p2` | python | oui | oui | oui | oui | 0.340 | 0.000 |
| `ERREUR-p3` | python | non | oui | non | non | 0.314 | 0.000 |
| `ERREUR-j1` | java | oui | oui | oui | oui | 0.306 | 0.000 |
| `ERREUR-j2` | java | oui | oui | oui | oui | 0.261 | 0.000 |
| `ERREUR-j3` | java | oui | oui | oui | oui | 0.261 | 0.000 |
| `ERREUR-c1` | cpp | oui | oui | oui | oui | 0.222 | 0.000 |
| `ERREUR-c2` | cpp | oui | oui | oui | oui | 0.290 | 0.000 |
| `ERREUR-c3` | cpp | oui | oui | oui | oui | 0.309 | 0.000 |
| `ERREUR-t1` | tous | oui | oui | oui | oui | 0.194 | 0.000 |

## 6. Ce que ce rapport ne prouve pas

1. **Le corpus reste petit** (36 fragments, 3 langages). À cette taille, une recherche par mots-clés réussit presque toujours : ces taux valident la **chaîne technique** (découpage, vecteurs, index HNSW, recherche hybride), pas la qualité d'un agent réel.
2. **Les vecteurs sont de démonstration.** Le plissement de hachage ne sépare pas finement le domaine du hors-domaine. C'est mesuré ci-dessus, et c'est la raison pour laquelle le refus n'est **pas** confié au seul seuil : il est aussi porté par le prompt système (étape 5) et par le filtre de sortie (étape 6). Avec un vrai modèle d'embeddings, la séparation est bien meilleure — le seuil devra être recalibré sur le corpus complet, avec cette même procédure.
3. **Les incidents de notation ne sont pas mesurés ici** : ils le sont côté Java, par les tests adversariaux de `GardeFouTest`.
4. **Le jeu de questions est gelé.** Il a été écrit et figé avant cette première mesure (`ia/corpus/MANIFESTE.json`) : le modifier après coup invaliderait la mesure.

