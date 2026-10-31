# Étape 3 — découpage et indexation : ce que la mesure dit

*Généré le 2026-10-27 par `ia/evaluer_etape3.py`. PostgreSQL + pgvector, vecteurs de démonstration (plissement de hachage 1536 d).*

## 1. Le protocole, avant les chiffres

Le jeu de 60 questions est gelé depuis l'étape 2. Le régler puis le mesurer reviendrait à mesurer le réglage. Il est donc partagé une fois :

| Moitié | Questions | Usage | Empreinte SHA-256 |
|---|---:|---|---|
| Réglage | 20 | choisir les paramètres | `3ce1146485586b05c679eea7…` |
| **Mesure** | **40** | **seuls chiffres publiés** | `36b164ca577ac2449ea79e80…` |

Règle de partage : par notion : première question de chaque langage + 2 transversales (VAR, ERREUR).

## 2. Ce qui change dans l'index

| | Étape 2 — index v1 | Étape 3 — index v2 |
|---|---|---|
| Unités indexées | 36 fragments (un bloc = un fragment) | **25 fragments** (découpés) |
| Taille | 145–437 car. | **323–584 car.** |
| Recouvrement | aucun | **15 % (90 car.) entre fragments consécutifs** |
| Contexte d'indexation | aucun | **« Cours · Langage · Type » préfixé pour la recherche, jamais pour la citation** |
| Score | `sim + 0,5 × lexical` | `sim + poids × lexical + bonus × couverture` — **paramètres réglés sur la moitié réglage** |

La couverture est la part des mots significatifs de la question réellement présente dans le fragment : c'est ce qui distingue `ArrayList` de « liste ». Le plissement de hachage ne sait pas faire cette différence ; la couverture, si.

## 3. Réglage (20 questions) — ce qui a été choisi

| Paramètre | v1 retenu | v2 retenu |
|---|---|---|
| `seuil_vecteur` | 0.0 | 0.15 |
| `poids_lex` | 0.0 | 0.0 |
| `bonus_couverture` | 0.0 | 0.15 |
| top-5 sur la moitié réglage | 100 % | 100 % |
| top-1 sur la moitié réglage | 80 % | 80 % |
| configurations essayées | 12 | 36 |

### La frontière que le seuil ne savait pas tenir (moitié réglage)

| Seuil | v1 top-5 | v1 fuites hors domaine | v2 top-5 | v2 fuites hors domaine |
|---:|---:|---:|---:|---:|
| 0.00 | 100 % | 4/4 | **100 %** | **4/4** |
| 0.05 | 90 % | 2/4 | **100 %** | **3/4** |
| 0.10 | 80 % | 1/4 | **100 %** | **2/4** |
| 0.15 | 70 % | 0/4 | **100 %** | **1/4** |

**C'est le résultat de l'étape.** Avec l'index v1, garder toutes les questions légitimes (top-5 100 %) oblige à accepter **toutes** les citations hors domaine ; remonter le seuil à 0,15 refuse proprement les quatre questions hors domaine mais fait tomber le top-5 à 70 %. Avec l'index v2, le même seuil de 0,15 conserve un top-5 de 100 % et ne laisse passer qu'**une** question hors domaine. Découper, préfixer le contexte et mesurer la couverture ne rendent pas seulement la recherche plus juste : ils rendent le seuil utilisable — et c'est le seuil qui protège l'agent du hors-domaine.

### Sensibilité au poids du lexical (moitié réglage)

| `poids_lex` | v1 top-1 | v1 top-5 | v2 top-1 | v2 top-5 |
|---:|---:|---:|---:|---:|
| 0.0 | 80 % | 100 % | 80 % | 100 % |
| 0.5 | 80 % | 100 % | 80 % | 100 % |
| 1.0 | 80 % | 100 % | 80 % | 100 % |

**Ce que ce tableau ne permet pas de dire.** Les trois poids donnent exactement le même résultat : sur 20 questions, l'échantillon ne permet pas de trancher. Le protocole retient donc la valeur la plus simple (`poids_lex = 0`) — et la moitié mesure, déjà dépouillée, n'autorise plus à revenir sur ce choix. La branche lexicale reste néanmoins active comme **filet de rappel** : un fragment devient candidat dès qu'il est lexicalement pertinent (`sim >= seuil OR lex > 0 OR couverture > 0`), même si son vecteur est faible. C'est ce filet qui rattrape les noms d'API (`ArrayList`, `out_of_range`). Avec un vrai modèle d'embeddings, la similarité cesse de doubler la recherche par mots et ce poids devra être réglé pour de bon — avec une nouvelle moitié de réglage, pas avec ces 60 questions.

## 4. Mesure (40 questions jamais utilisées pour le réglage)

| Mesure | Étape 2 (v1) | **Étape 3 (v2)** | Écart |
|---|---:|---:|---:|
| top-1 : la bonne section sort en premier | 72 % | **70 %** | -3 pts |
| top-5 : la bonne section est dans les cinq premiers | 98 % | **100 %** | +3 pts |
| témoin présent dans le fragment cité en premier | 75 % | **72 %** | -3 pts |
| témoin présent dans l'un des cinq fragments | 92 % | **98 %** | +5 pts |
| top-1 sans filtre de langage | 60 % | **68 %** | +8 pts |

Critère de sortie de l'étape 3 : **top-5 ≥ 85 %** sur les questions de référence. Résultat : **100 %** — tenu.

### Par langage (moitié mesure)

| Langage | Questions | top-1 v1 | top-1 v2 | top-5 v1 | top-5 v2 |
|---|---:|---:|---:|---:|---:|
| python | 12 | 75 % | **75 %** | 100 % | **100 %** |
| java | 12 | 83 % | **75 %** | 100 % | **100 %** |
| cpp | 12 | 67 % | **67 %** | 92 % | **100 %** |
| tous | 4 | 50 % | **50 %** | 100 % | **100 %** |

### Questions hors domaine

Avec la configuration retenue, l'index v2 cite un fragment pour **1 / 4** questions hors domaine (v1 : 4 / 4). Ces fuites restent arrêtées en aval par le prompt système et le filtre de sortie : le seuil n'est jamais le seul rempart.

## 5. Ce que cette étape ne prouve pas

- **Le top-1 ne progresse pas, et ce n'est pas un détail** (72 % → 70 %, écart dans le bruit à 40 questions). C'est le plafond du vecteur de démonstration, pas celui du découpage : ajouter du contexte et de la couverture ne compense pas un vecteur qui ne comprend pas la phrase. Le gain réel de l'étape porte sur le **top-5** (98 % → 100 %), sur le **témoin dans le top-5** (92 % → 98 %) et sur le **refus hors domaine** (4 fuites sur 4 → 1 sur 4).
- **Le classement ne repose plus sur un seul signal.** Même avec un poids lexical nul, l'admission des candidats reste hybride : la branche lexicale rattrape les fragments que le vecteur rate. Ce point est mesuré ci-dessus, pas supposé.
- **Les vecteurs restent de démonstration.** Le gain mesuré vient du découpage, du contexte d'indexation et de la couverture — pas d'une meilleure sémantique. Avec un vrai modèle d'embeddings, les mêmes paramètres devront être réglés à nouveau, avec la même partition et le même protocole.
- **40 questions restent peu.** L'intervalle de confiance d'un taux à 40 questions est large (± 7 points environ) : les écarts inférieurs à 10 points ne doivent pas être lus comme des victoires.
- **La partition est un choix**, pas une évidence : elle est écrite, datée et son empreinte est publiée, ce qui permet de la contester — pas de la refaire en silence.
- **Le corpus ne change pas ici** : l'étape 3 découpe et indexe ; elle n'ajoute aucun cours. Une question sans réponse dans le corpus restera sans réponse, quel que soit l'index.
