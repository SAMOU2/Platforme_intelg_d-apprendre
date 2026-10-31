# Guide de rédaction d'un prompt (étape 5)

Ce guide dit comment modifier l'agent sans casser ce que les autres étapes garantissent.

## 1. Où l'on écrit

| Fichier | Rôle | Modifiable à la main ? |
|---|---|---|
| `gabarit.md` | le texte du prompt, avec ses emplacements | **oui**, c'est le seul |
| `generer_prompt.py` | compose le prompt depuis le gabarit et le contrat de refus | oui |
| `system-<version>.md` | prompt généré, copié sur le classpath par le pom | **non** |
| `sortie-schema.json` | schéma de la sortie, généré par le même script | **non** |
| `src/main/resources/.../cas-de-refus.json` | les 8 libellés de refus, source unique | oui (étape 1) |

```bash
cd ihm-2026/ia
python3 prompts/generer_prompt.py             # régénère
python3 prompts/generer_prompt.py --verifier  # échoue si le fichier livré a dérivé
```

## 2. Les quatre règles d'écriture

1. **Ne jamais recopier un libellé de refus.** Le vocabulaire de la section 5 est extrait du contrat
   par le générateur ; un libellé écrit à la main dans le gabarit divergerait au premier changement.
2. **Décrire un comportement vérifiable.** « Sois bienveillant » ne se teste pas ; « trois phrases
   maximum, en français, en tutoyant » se vérifie dans `GardeFou` et dans les tests.
3. **Un interdit = une garantie dans le code.** Une phrase du prompt qui ne correspond à aucun
   contrôle est une intention, pas une règle. `ContratRefusTest` refuse les garanties sans point
   d'appel.
4. **Un exemple few-shot est un cas de test qui parle au modèle.** Les deux exemples de refus sont
   tirés du contrat (`DECISION_DEMANDEE`, `HORS_PERIMETRE`), pas inventés : ce que l'on montre au
   modèle est exactement ce que l'on exige du logiciel.

## 3. Changer de version

1. Incrémenter `VERSION` dans `generer_prompt.py` (`system-v3`, …).
2. Régénérer : un nouveau fichier `system-v3.md` apparaît ; l'ancien reste comme archive.
3. Mettre à jour `ModeleSimule.VERSION_PROMPT` : c'est cette constante qui est journalisée à chaque
   appel, avec la question et le motif de refus.
4. Relancer `mvnw.cmd test` : `FormatSortieTest` vérifie que le prompt du classpath porte bien la
   version courante, contient les huit libellés du contrat, et que le schéma de sortie accepte une
   réponse citée comme un refus.
5. Consigner le changement dans `CHANGELOG.md` avec sa raison — pas seulement sa date.

## 4. Ce que l'on ne fait pas

- Modifier `system-<version>.md` directement : le script le réécrirait, et `--verifier` le
  signalerait comme une dérive.
- Ajouter un champ à la sortie sans toucher au schéma : `SortieIa` rejette les champs inconnus, donc
  le champ serait silencieusement perdu.
- Promettre une chose que le code n'applique pas : le prompt ne protège rien, il oriente. La
  garantie est dans `GardeFou` et dans `SortieIa`.
