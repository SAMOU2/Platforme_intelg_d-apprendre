# Base de données — PostgreSQL 16

## Démarrer
```bash
cd db
docker compose up -d          # crée la base, applique 01-schema.sql puis 02-seed.sql
docker compose logs -f postgres
```

## Se connecter
```bash
docker exec -it ihm2026-postgres psql -U ihm2026 -d ihm2026
```
Chaîne de connexion : `jdbc:postgresql://localhost:5433/ihm2026` (utilisateur `ihm2026`, mot de passe `ihm2026`).
Le port hôte par défaut est `5433` pour éviter un conflit avec un PostgreSQL déjà installé sur
Windows. Il peut être remplacé avec la variable `IHM_DB_PORT`.
Pour une base existante, appliquer aussi `08-auth-sessions.sql` puis
`09-inscriptions-et-codes.sql` une fois avec `psql`.

## Réinitialiser
```bash
docker compose down -v && docker compose up -d
```

## Contenu
| Fichier | Rôle |
|---|---|
| `01-schema.sql` | tables, énumérations, contraintes, index, trigger de protection des validations |
| `02-seed.sql` | 3 langages, 3 parcours, 15 niveaux, cours du niveau 3 Python, 3 questions, comptes de démonstration (mots de passe hachés) |
| `03-corpus-ia.sql` | corpus de l'agent : cours Java et C++ du niveau 1, table `ia_fragments` (pgvector) |
| `04-ia-index.sql` | fonction `rechercher_fragments()` : HNSW + plein texte français + seuil |
| `05-corpus-v1.sql` | **corpus v1** : 18 cours / 36 blocs (6 notions × 3 langages), retrait des cours de démonstration remplacés, ré-extraction de `ia_fragments` |
| `06-index-v2.sql` | **étape 3** : `ia_fragments_v2` (fragments de 300 à 600 caractères, contexte d'indexation, `CHECK caracteres <= 600`) et `rechercher_fragments_v2()` — HNSW + plein texte + couverture lexicale |
| `07-auth.sql` | Migration idempotente des anciens mots de passe vers bcrypt (pgcrypto) et unicité de l'e-mail sans distinction de casse |
| `08-auth-sessions.sql` | Empreintes de jetons, expiration des sessions et suivi des échecs de connexion |
| `09-inscriptions-et-codes.sql` | Statut d'approbation des comptes et stockage des codes temporaires de connexion |

Le seed de démonstration utilise le mot de passe `demo` pour les comptes listés, en développement
uniquement. Le mot de passe est stocké sous forme bcrypt ; ne pas conserver ces identifiants en
production. Pour une base déjà créée, appliquer explicitement `07-auth.sql` avec `psql` ; les scripts
d'initialisation Docker ne sont exécutés automatiquement que lors de la création initiale du volume.
La migration refuse de continuer si plusieurs comptes partagent une adresse e-mail après normalisation
de la casse ; résoudre ces doublons avant de la relancer.

Le service serveur lit ses accès PostgreSQL depuis `IHM_DB_URL`, `IHM_DB_USER` et `IHM_DB_PASSWORD`.
Ne pas inscrire le mot de passe dans le dépôt, les arguments du processus ou les journaux.
Les nouveaux mots de passe de compte doivent contenir au moins 12 caractères et ne pas dépasser
72 octets UTF-8 (limite de bcrypt).

## Service d'authentification HTTPS (IHM-004)

Le service d'authentification est un serveur autonome, distinct du client JavaFX et du serveur de
salle. Pour une base déjà créée, appliquer la migration des sessions après `07-auth.sql` :

```powershell
docker exec ihm2026-postgres psql -v ON_ERROR_STOP=1 -U ihm2026 -d ihm2026 `
  -f /docker-entrypoint-initdb.d/08-auth-sessions.sql
docker exec ihm2026-postgres psql -v ON_ERROR_STOP=1 -U ihm2026 -d ihm2026 `
  -f /docker-entrypoint-initdb.d/09-inscriptions-et-codes.sql
```

Le point d'entrée Java est `fr.ihm2026.api.service.ServeurAuthentificationHttps`. Depuis une
configuration d'exécution de l'IDE, renseigner les variables d'environnement suivantes puis lancer
sa méthode `main` :

| Variable | Obligatoire | Valeur par défaut / rôle |
|---|---:|---|
| `IHM_DB_URL` | oui | URL JDBC PostgreSQL, par exemple `jdbc:postgresql://localhost:5433/ihm2026` |
| `IHM_DB_USER` | oui | Utilisateur PostgreSQL |
| `IHM_DB_PASSWORD` | oui | Mot de passe PostgreSQL ; ne pas le placer dans le dépôt |
| `IHM_TLS_KEYSTORE` | oui | Chemin vers le magasin PKCS12 contenant le certificat et la clé privée du serveur |
| `IHM_TLS_PASSWORD` | oui | Mot de passe du magasin ; le fournir par l'environnement d'exécution |
| `IHM_TLS_KEYSTORE_TYPE` | non | `PKCS12` |
| `IHM_AUTH_PORT` | non | `8443` |
| `IHM_BOOTSTRAP_ADMIN_NAME` | si aucun admin n'existe | Nom du premier administrateur |
| `IHM_BOOTSTRAP_ADMIN_EMAIL` | si aucun admin n'existe | E-mail du premier administrateur |
| `IHM_BOOTSTRAP_ADMIN_PASSWORD` | si aucun admin n'existe | Secret initial, fourni uniquement par l'environnement |
| `IHM_SMTP_HOST` | requis pour l'envoi d'e-mails | Hôte SMTP |
| `IHM_SMTP_PORT` | non | `587` (STARTTLS) |
| `IHM_SMTP_FROM` | requis pour l'envoi d'e-mails | Adresse d'expédition |
| `IHM_SMTP_USER` | non | Compte SMTP si authentification requise |
| `IHM_SMTP_PASSWORD` | non | Secret SMTP, fourni par l'environnement |

Au premier démarrage, si aucun compte administrateur n'existe, les trois variables
`IHM_BOOTSTRAP_ADMIN_*` sont obligatoires pour créer un administrateur actif et accepté. Ce compte
est créé une seule fois; supprimer ces variables après l'amorçage. SMTP doit être configuré pour
recevoir les codes de connexion et les notifications de décision.

Utiliser un certificat approuvé par tous les postes clients, dont le nom DNS ou l'adresse IP
correspond à l'adresse utilisée par les postes. Ne pas exposer le service avant d'avoir configuré
ce certificat et protégé ses secrets. Le serveur ne démarre pas si la configuration TLS ou la
connexion PostgreSQL est absente ; il ne bascule jamais vers HTTP. Le handler rejette également
les requêtes reçues sur un échange HTTP avec le statut `426 Upgrade Required`.

Routes disponibles :

| Méthode et route | Accès | Résultat |
|---|---|---|
| `POST /api/auth/inscription` | Public, HTTPS uniquement | Crée une demande `EN_ATTENTE` après vérification du PIN individuel lié à l'e-mail et au rôle |
| `POST /api/auth/connexion` | Public, HTTPS uniquement | Vérifie les identifiants et envoie un code à usage unique par e-mail |
| `POST /api/auth/code` | Public, HTTPS uniquement | Vérifie le code et retourne le jeton et l'identité chargés depuis PostgreSQL |
| `GET /api/auth/session` | Jeton actif | Vérifie et renouvelle l'expiration d'inactivité |
| `POST /api/auth/deconnexion` | Jeton actif | Révoque la session et retourne `204` |
| `GET /api/admin/comptes` | Administrateur | Liste les comptes et demandes d'inscription |
| `PATCH /api/admin/comptes/decision` | Administrateur | Accepte ou refuse une demande et notifie le demandeur par e-mail |
| `POST /api/admin/invitations` | Administrateur | Génère un PIN individuel et l'envoie à l'adresse e-mail liée |

Une inscription, quel que soit le rôle demandé, reste en attente jusqu'à l'acceptation d'un
administrateur. Le PIN d'invitation est valable 48 heures, lié à un seul e-mail et rôle, limité à
cinq essais, à usage unique et stocké uniquement sous forme bcrypt dans `invitations_inscription`.
Il n'est jamais conservé en clair dans un fichier JSON. Un compte refusé ou en attente ne peut pas
se connecter. Le code de connexion par e-mail est également à usage unique, haché en base et expire
après 10 minutes. L'envoi d'e-mail exige une configuration SMTP valide. La découverte du serveur
local se fait automatiquement en UDP, sans PIN de salle.
Si l'envoi du PIN échoue après sa création, l'interface affiche l'erreur SMTP; une nouvelle
génération invalide l'invitation précédente et envoie un nouveau PIN.

Ordre d'exécution : `01` → `02` → `03` → `05` → `04` → `06`, puis :
```bash
cd ia
python3 indexer.py --mode blocs   # ia_fragments   : un bloc = un fragment (étape 2)
python3 indexer.py                # ia_fragments_v2 : découpage 300-600, recouvrement 15 % (étape 3)
```
Les deux tables coexistent volontairement : sans elles, aucune comparaison avant/après n'est
possible sur le même jeu de questions. Mesure : `python3 evaluer_etape3.py`.
`05-corpus-v1.sql` doit précéder `04` : il réécrit le contenu de `ia_fragments`, et `04` crée la
fonction qui le lit. Le fichier est **idempotent** (relançable sans doublon) et il est **généré** :
ne pas le modifier à la main, relancer `python3 ia/corpus/construire.py`.

## Encodage : la base doit être en UTF8
Le corpus est en français. Sur une base créée en `SQL_ASCII`, `ia/indexer.py` échoue sur
`UnicodeDecodeError` (constaté le 27/10/2026). Vérifier puis recréer si besoin :
```sql
SHOW server_encoding;                                    -- doit renvoyer UTF8
CREATE DATABASE ihm2026 WITH TEMPLATE template0 ENCODING 'UTF8'
       LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8';
```
Le conteneur `docker-compose.yml` crée déjà la base en UTF8.

## Vérification effectuée (27/10/2026, corpus v1)
Scripts exécutés sur **PostgreSQL 17.11 + pgvector**, base vide créée en UTF8 :
```
01-schema.sql     → OK (15 tables, 5 énumérations, 1 trigger)
02-seed.sql       → OK (7 comptes, 15 niveaux, 12 propositions, 5 progressions)
03-corpus-ia.sql  → OK (3 cours, 11 fragments de démonstration)
05-corpus-v1.sql  → OK (18 cours, 36 blocs) — rejoué une seconde fois : aucun doublon
04-ia-index.sql   → OK (index HNSW + GIN, fonction de recherche)
ia/indexer.py     → 36 vecteurs écrits (12 par langage)
ia/evaluer.py     → top-1 73 %, top-5 92 %, témoin top-1 77 % (60 questions gelées)
```
Trois anomalies réelles trouvées par ces exécutions et corrigées :
1. `02-seed.sql` référençait `v.rang` dans un `JOIN` antérieur à sa déclaration (invisible pour un
   analyseur statique) — corrigé en v0.2.0 ;
2. une base en `SQL_ASCII` faisait échouer l'indexation sur `UnicodeDecodeError` — l'encodage UTF8
   est désormais une condition écrite ci-dessus ;
3. les cours de démonstration (`Fonctions et modules`, `Méthodes et valeur de retour`,
   `Fonctions, retour et surcharge`) redisaient une partie de la grille et faisaient citer deux
   fois la même notion : ils sont repassés en `BROUILLON` par `05-corpus-v1.sql`, jamais supprimés.

Après `05-corpus-v1.sql` : **6 cours et 12 blocs PUBLIE par langage**, exactement (contrôle SQL
exécuté) ; les 18 sections produites par le corpus sont identiques à celles d'`ia_fragments`.

## Vérification de l'étape 3 (27/10/2026)
```
06-index-v2.sql  → OK (table ia_fragments_v2, CHECK taille, HNSW, GIN, fonction v2)
indexer --mode blocs → 36 fragments (145-437 car.)
indexer             → 25 fragments (305-584 car.), 0 hors bornes
evaluer_etape3.py   → réglage sur 20 questions, mesure sur 40 :
                      top-5 100 % (v1 : 98 %), fuites hors domaine 1/4 (v1 : 4/4)
test_decoupage.py   → 8 tests verts (taille, recouvrement, coupes propres, couverture du corpus)
```
Anomalie réelle trouvée par les tests de propriété : la première règle de recouvrement prenait
« la dernière phrase » du fragment précédent, soit 40 caractères au lieu de 90 sur le cours
*Boucles en C++*. La mesure ne le voyait pas ; le test, si.

## Points de conception portés par le schéma
- **Un seul rôle par compte** : colonne `utilisateurs.role` (énumération), pas de table de jointure — l'invariant du modèle AD1 est impossible à violer.
- **Un niveau validé le reste** : trigger `trg_proteger_validation` — modifier un seuil ne réécrit jamais le passé (décision E2).
- **Une seule bonne réponse par QCM** : index unique partiel `uq_une_bonne_reponse`.
- **Trois toolchains épinglés** : `langages.version` + `langages.image_execution` (Python 3.12, OpenJDK 21 LTS, C++20) — RG-24, BNF-L5.
- **L'IA ne note pas** : aucune colonne de note dans `aides_ia` ; l'agent n'a pas de chemin vers `tentatives`/`progressions` (RG-13).
- **Traçabilité** : `journal_audit` en JSONB + index sur la date (BNF-06).

## Migration 10 — correction humaine

`10-correction-enseignant.sql` ajoute les affectations et métadonnées de copie sans
réinitialiser les tables existantes. Sauvegarder la base, arrêter HTTPS puis appliquer
avec `psql -v ON_ERROR_STOP=1 -f ...` sur les volumes existants. Instructions et recette :
[parcours de correction](../docs/parcours-correction-enseignant.md).
