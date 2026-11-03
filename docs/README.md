# Livrables de conception

Référence logicielle : application **0.14.4**, branche `main` après la PR #31 (`2ae972b`), migrations PostgreSQL 01 à 14.

| Fichier | Version | Contenu |
|---|---|---|
| `analyse-besoins-IHM.pdf` | v2.0 | Analyse des besoins alignée sur la version livrée : 57 BF, 18 BNF, 20 exigences IHM, 24 règles de gestion et 14 cas d’utilisation |
| `canvas-document-analyse-besoins.pdf` | v2.0 | Copie du même PDF d’analyse v2.0, pour conserver l’ancien nom de remise sans garder une version divergente |
| `Diagrammes-du-projet.pdf` | v2.0 | 11 vues : cas d’utilisation, données, états-transitions, 4 séquences, activité, composants et déploiement |
| `Modeles-de-taches-du-projet-IHM.pdf` | v2.0 | 11 modèles HTA (A0 à UX1) pour découverte/connexion, apprentissage, tests, correction, administration, IA et accessibilité |
| `Cahier-des-charges.pdf` | v1.0 | Périmètre, exigences fonctionnelles et non fonctionnelles, règles métier, architecture, livrables, risques et scénarios de recette |
| `Architecture-des-fichiers.pdf` | v1.0 | Document d’architecture de fichiers non régénéré dans ce lot ; en cas de contradiction, les documents v2.0 et le cahier des charges v1.0 priment |

## Sources et régénération

Les sources modifiables des quatre documents sont dans `docs/sources-v2/` :

- `analyse-des-besoins-v2.md`
- `diagrammes-du-projet-v2.md`
- `modeles-de-taches-v2.md`
- `cahier-des-charges-v1.md`

Les 12 schémas PNG utilisés par ces documents sont générés dans `docs/preuves/diagrammes-v2/`. Ce sont des schémas de conception, **pas des captures de l’application JavaFX**.

Avec Python, Pillow et ReportLab installés dans l’environnement de génération :

```bash
python3 outils/generer_documents_v2.py
```

Le script régénère les PNG puis les PDF ci-dessus. Les éléments marqués « recette Windows » dans les documents ne doivent pas être présentés comme prouvés sans exécution réelle sous Windows (Maven/JUnit, JavaFX, Docker/PostgreSQL, réseau, SMTP/NVIDIA ou NVDA selon le scénario).
