# Plateforme d'apprentissage Java · Python · C++ — client JavaFX

**Projet d'IHM 2026** · version **0.14.4** (plateforme complète, agent d'assistance intégré, corpus v1, index v2, stratégie de modèle, prompt v2, sortie vérifiée, modèle local entraîné, fenêtre macOS, animations, style rétro phosphore, **postes en réseau local**)
Application de bureau **JavaFX 25**, thèmes **AtlantaFX**, style **CSS3**, base **PostgreSQL 16**.

---

## 1. Ce que contient cette version (v0.1)

| Domaine | État | Détail |
|---|---|---|
| Socle technique | ✅ | fenêtre 1280×800, navigation (routeur), thème clair/sombre, tâches asynchrones |
| Style | ✅ | 5 feuilles CSS3 : `app.css`, `theme-light.css`, `theme-dark.css`, `syntax/syntax-{java,python,cpp}.css` — **aucune couleur dans le Java** |
| Apprenant | ✅ | poste dédié connecté au serveur ; parcours, cours, exercice, test et résultats |
| Enseignant | ✅ | poste dédié connecté au serveur ; création des niveaux et consultation des résultats |
| Administrateur | ✅ | poste dédié connecté au serveur ; comptes et rôles |
| Agent d'IA | ✅ | **intégré aux 3 rôles** : apprenant (Cours + Exercice), enseignant (synthèse), administrateur (journal). Modèle local v1 + corpus de 36 fragments ; modèle v2 expérimental. Ce modèle local de classement/récupération n'est pas un LLM génératif. Fermé côté service pendant un test |
| Authentification | ✅ | connexion par e-mail/mot de passe sur HTTPS ; inscription publique apprenant uniquement ; comptes enseignant/admin créés par l'administrateur |
| Données | 🟡 simulées | `api/mock/MockBackend` — mêmes valeurs que `db/02-seed.sql` |
| Base PostgreSQL | ✅ | `db/01` à `04` — **exécutés sur PostgreSQL 17 + pgvector**, pas seulement écrits |
| API métier REST | ❌ v0.3 | les données métier utilisent encore `api/mock/MockBackend`; les contrats existants ne sont pas encore branchés au client |
| Tests | ✅ | **155 tests** JUnit, dont la matrice d'accès entre apprenant, enseignant et administrateur |
| Vérification visuelle | ✅ | `SmokeTest` : parcours des écrans autorisés pour le rôle configuré + captures PNG |

---

## 2. Lancer l'application

### Déploiement centralisé (PC1 uniquement pour Docker)

Docker/PostgreSQL et le service HTTPS tournent **uniquement sur PC1**. Les clients
administrateur, enseignant et apprenant n'ont pas besoin de Docker ni d'accès direct à la base.
Le champ « Adresse IP du PC serveur » permet maintenant une connexion explicite à PC1 ;
vide, il utilise la découverte automatique. La variable `IHM_SERVEUR` préremplit ce champ
via les lanceurs de rôle Windows/Linux. PostgreSQL est publié uniquement sur la boucle locale
sur PC1 (`127.0.0.1:5433`).

Les lanceurs Windows existent aussi en `.bat`. L'écran de connexion est désormais défilant
pour garder les champs et actions accessibles sur petit écran.

Voir [le guide et les limites des preuves](docs/preuves/README.md) pour la configuration,
les contrôles exécutés et les PNG. Les anciens passages imposant la découverte automatique
sans saisie d'IP décrivent le fonctionnement antérieur.


Le backlog, les priorités, les responsables par rôle et la définition de terminé sont centralisés
dans [docs/GESTION-PROJET.md](docs/GESTION-PROJET.md).

### Pré-requis
- **Un JDK 25 LTS** — `java -version`
- **Maven : facultatif.** Le projet contient le **Maven Wrapper** (`mvnw` / `mvnw.cmd`) : il télécharge
  Maven 3.9.9 tout seul au premier lancement. Rien à installer, rien à ajouter au `PATH`.

### Commandes
```bash
./mvnw compile       # compile (Windows : mvnw.cmd compile)
./mvnw test          # tests
```

`mvn` fonctionne aussi si Maven est déjà installé. `./mvnw` suffit sur toute machine où le JDK est
présent : c'est ce qui rend le livrable reproductible chez un correcteur sans outillage préalable.

**Pour utiliser l'application, démarrer les deux services sur le poste serveur**, puis lancer le
script du poste attribué dans `postes/` — par exemple `2-administrateur-PC2.cmd`,
`3-enseignant-PC3.cmd` ou `4-apprenant-PC4.cmd`. Le poste client utilise `mvn` s'il est installé,
sinon `mvnw.cmd`.
Le script utilise `JAVA_HOME` s'il pointe vers un JDK, sinon cherche `javac` dans le `PATH`, puis
essaie l'installation Liberica connue (`C:\Program Files\BellSoft\LibericaJDK-25-Full`). Il refuse
les versions autres que Java 25 LTS et affiche la version détectée avant de lancer `mvn javafx:run`.
Les équivalents Windows, Linux et
macOS de chaque poste se trouvent dans `postes/`.
Le serveur de salle est découvert automatiquement sur le réseau local : aucune adresse IP ni PIN
de salle n'est à saisir. Le rôle n'est pas sélectionnable dans le script ni dans la fenêtre : il
est fixé par le poste et vérifié par le serveur HTTPS avant l'ouverture de l'écran correspondant.

### Authentification réelle (PC1)

L'authentification est servie par `postes\authentification-PC1.bat`, séparément du serveur de salle
`postes\1-serveur-PC1.cmd`. Initialiser la base et exécuter au minimum `db\01-schema.sql`,
`db\02-seed.sql`, `db\07-auth.sql`, `db\08-auth-sessions.sql` et `db\09-inscriptions-et-codes.sql`.
L'amorçage d'une base sans administrateur nécessite de configurer les trois variables
`IHM_BOOTSTRAP_ADMIN_NAME`, `IHM_BOOTSTRAP_ADMIN_EMAIL` et `IHM_BOOTSTRAP_ADMIN_PASSWORD` avant
le premier démarrage du service HTTPS. Elles ne servent qu'à créer le premier administrateur ; si
aucun administrateur n'existe et que la configuration manque, le service refuse de démarrer.

Pour une démonstration locale avec les données de `db\02-seed.sql`, les comptes actifs sont
`nadia.cherif@univ.dz` (administrateur), `sofia.meziane@univ.dz` (enseignant) et
`amina.bouzid@univ.dz` (apprenant) ; leur mot de passe commun est `demo`. Ces identifiants sont
réservés au jeu de données de développement : ne pas les conserver dans une base de production.

Pour faciliter le double-clic sous Windows, copier
`postes/configuration-PC1.exemple.cmd` vers `postes/configuration-PC1.local.cmd`,
puis renseigner ce fichier **local uniquement**. Le lanceur HTTPS le charge automatiquement.
Le fichier local et `postes/secrets/` sont exclus de Git : ne jamais y publier de secrets.
Il faut toujours fournir un vrai magasin TLS et une configuration SMTP fonctionnelle.
Le serveur de salle affiche maintenant les IPv4 candidates avant toute compilation ;
choisir l'adresse de la carte du même réseau que les clients, pas une carte VPN ou Docker.

Avant le lancement du service, configurer dans l'environnement Windows du compte qui l'exécute :
`IHM_DB_URL`, `IHM_DB_USER`, `IHM_DB_PASSWORD`, `IHM_TLS_KEYSTORE`, `IHM_TLS_PASSWORD`,
`IHM_SMTP_HOST` et `IHM_SMTP_FROM`. Pour un serveur SMTP authentifié, ajouter
`IHM_SMTP_USER` et `IHM_SMTP_PASSWORD`; `IHM_SMTP_PORT` vaut 587 par défaut. Ces paramètres
doivent être injectés dans l'environnement du serveur, jamais enregistrés dans le dépôt.
`IHM_AUTH_PORT` est facultatif (8443 par défaut) ; son type de magasin TLS est `PKCS12` par défaut
et peut être changé avec `IHM_TLS_KEYSTORE_TYPE`. Le fichier batch ne contient aucun secret.
Laisser la fenêtre du service ouverte. Le port HTTPS doit être autorisé dans le pare-feu.

Chaque poste de rôle propose « Créer un compte » et « Se connecter ». Pour une inscription,
l'administrateur crée dans « Comptes et rôles » un PIN individuel lié à l'e-mail et au rôle du
futur compte. Le serveur l'envoie automatiquement à cette adresse par e-mail. Le PIN expire après
48 heures, ne peut être utilisé qu'une fois et n'est jamais affiché dans l'interface administrateur.
PostgreSQL conserve uniquement son empreinte bcrypt, jamais le PIN en clair. Toute demande reste
**en attente** jusqu'à la décision d'un administrateur; seuls les comptes acceptés peuvent se
connecter. L'acceptation ou le refus est notifié par e-mail. Après acceptation, une connexion avec
e-mail et mot de passe déclenche l'envoi d'un code à usage unique par e-mail. Ce code expire après
10 minutes et doit être saisi pour terminer la connexion.
Le mot de passe doit contenir au moins 12 caractères et au plus 72 octets UTF-8; PostgreSQL le
conserve haché. La découverte utilise UDP sur le port local du serveur; autoriser ce port UDP ainsi
que les ports HTTP local et HTTPS dans le pare-feu. `db\02-seed.sql` fournit des comptes de
développement, à ne pas conserver en production.

Les clients utilisent l'hôte découvert automatiquement pour se connecter en HTTPS sur le port
8443. Le certificat présenté doit être valide pour cet hôte et approuvé par le JDK client (ou par le truststore configuré pour le
JDK) : ne pas désactiver la validation TLS. Pour un port personnalisé, définir
`JAVA_TOOL_OPTIONS=-Dihm.auth.port=<port>` sur chaque poste client. La session est fermée côté
serveur par le bouton de sortie ou à la fermeture de la fenêtre ; elle expire aussi après 30 minutes.

Cette authentification vérifie l'identité et le rôle pour l'accès à l'interface. Les écrans
métiers s'appuient encore sur les données simulées ; le branchement des opérations métier et leur
autorisation côté API restent à faire avant un déploiement de production.

Pour chaque rôle, utiliser le lanceur standard correspondant : `2-administrateur-PC2.cmd`,
`3-enseignant-PC3.cmd` ou `4-apprenant-PC4.cmd` sous Windows (les fichiers `.sh` sont leurs
équivalents Linux/macOS). Ces lanceurs donnent accès aux fonctionnalités du rôle et utilisent le
modèle v1 recommandé. Le modèle v2 reste expérimental et n'a pas de lanceur destiné aux utilisateurs.

Le lancement direct sans `--role=` affiche un message de configuration et ne donne aucun accès.
Les écrans d'un rôle s'ouvrent seulement après connexion au serveur ; il n'existe plus d'entrée
hors ligne ni de choix de rôle à l'écran.

> Les données métier restent simulées dans cette version. La gestion des comptes passe par l'API
> HTTPS et PostgreSQL lorsqu'une session administrateur est ouverte. Le rôle fixé par un script ne
> prouve pas l'identité : c'est la session PostgreSQL/HTTPS qui authentifie le compte, et la connexion
> est refusée si son rôle ne correspond pas au poste.

### Dépanner la connexion et vérifier les corrections

**Si le service HTTPS ne démarre pas :** la console affiche désormais un diagnostic ciblé
(port déjà occupé, magasin TLS absent, clé privée/mot de passe incorrect, certificat expiré,
identifiants PostgreSQL refusés ou migrations manquantes). Un magasin contenant uniquement
un certificat de confiance est refusé dès le démarrage : le serveur exige sa clé privée.
Après création du premier administrateur, une configuration d'amorçage partiellement retirée
ne bloque plus les redémarrages. Sans administrateur, les trois variables restent obligatoires.
Si Docker utilise déjà un volume, les nouveaux scripts SQL doivent être appliqués explicitement ;
ne pas supprimer le volume pour tenter de résoudre une migration manquante.


- Démarrer **les deux services** sur PC1 : serveur de salle et service HTTPS
  `postes\authentification-PC1.bat`. La découverte du serveur de salle ne garantit pas que
  le service HTTPS écoute sur 8443.
- Si le certificat est refusé, vérifier son nom/SAN (l'hôte découvert est généralement une IP)
  et la confiance du JDK client. Ne pas désactiver la vérification TLS.
- Si aucun code n'arrive, vérifier la configuration SMTP du compte qui lance le service.
  Le transport utilise STARTTLS obligatoire. Les requêtes qui envoient un e-mail disposent
  désormais de 60 secondes, au lieu de 8, pour attendre les échanges SMTP.
- Après cinq mauvais codes ou dix minutes, cliquer **Annuler**, puis se reconnecter pour
  recevoir un nouveau code. Un nouveau code invalide le précédent.
- Si le code a été accepté mais que le serveur de salle est inaccessible (ou que le rôle ne
  correspond pas au poste), l'écran revient à la connexion : il ne redemande plus un code
  déjà consommé. L'annulation est bloquée pendant la validation et les appuis répétés sur
  Entrée ne lancent plus de requêtes concurrentes.
- La consommation du code et la création de session sont maintenant atomiques : un échec
  d'insertion PostgreSQL laisse le code utilisable pour un nouvel essai.

Vérification : `mvnw.cmd test` sous Windows, ou `bash mvnw test` sous Linux, avec un JDK 25.
Les tests `PostgresAuthentificationServiceIntegrationTest` exigent une **base de test dédiée**,
les migrations `01`, `02`, `07`, `08`, `09` et les variables `IHM_DB_URL`, `IHM_DB_USER`,
`IHM_DB_PASSWORD`. Sans `IHM_DB_URL`, ces tests sont ignorés. Le test de panne d'insertion
ajoute puis retire une contrainte temporaire dans cette base ; ne pas le lancer en production.

### Parcours de démonstration conseillé
1. Démarrer le serveur depuis le poste dédié, puis lancer un poste par rôle avec son script.
2. Le poste **apprenant** ouvre *Parcours* : la frise montre 5 niveaux (validé / en cours / verrouillé).
3. Cliquer un niveau → **Cours** (3 onglets Java / Python / C++ + bloc de code coloré).
4. **Exercice** : supprimer un `;` puis *Exécuter* → l'erreur est affichée **ligne par ligne**.
5. **Passer le test** → *Soumettre* → **Résultat** : note, seuil, points manquants, cours à revoir.
6. Le poste **enseignant** règle seuil / tentatives / période, puis *Publier* (confirmation).
7. Le poste **administrateur** crée, active ou désactive un compte (confirmation) ; la fermeture
   de la fenêtre et le bouton de sortie préviennent aussi le serveur de la déconnexion.
8. Le bouton **◐** dans chaque barre bascule le thème clair / sombre.

---

## 3. Base de données PostgreSQL

```bash
cd db
docker compose up -d     # crée la base et applique schéma + jeu de données
```
Voir **`db/README-db.md`** (connexion `jdbc:postgresql://localhost:5433/ihm2026`).

Points de conception déjà portés par le schéma :
- un seul rôle par compte (colonne `utilisateurs.role`) ;
- **un niveau validé ne peut pas être invalidé** (trigger `trg_proteger_validation`) ;
- une seule bonne réponse par QCM (index partiel) ;
- toolchains épinglés : Python 3.12 · OpenJDK 21 LTS · C++20 ;
- l'agent IA n'a **aucune colonne de note** : il ne note pas, ne valide pas, ne débloque pas.

---

## 4. Organisation du code

```
src/main/java/fr/ihm2026/
├── App.java            point d'entrée + table des routes
├── core/               Router · Session · ThemeManager · TaskRunner · i18n
├── api/                ApiClient (HTTP/JSON) · dto/ · service/ · mock/
├── model/              NiveauVM · CoursVM · ResultatVM · UtilisateurVM · EtatNiveau
└── ui/
    ├── commun/         NavBar · CarteNiveau · FriseNiveaux · ConsoleSortie · KpiCard · Dialogue
    ├── apprenant/      Login · Parcours · Cours · Exercice · Test · Resultat · TableauBord
    ├── enseignant/     Conditions · Resultats
    ├── admin/          Comptes
    └── ia/             PanneauAssistant
src/main/resources/fr/ihm2026/
├── css/                app.css · theme-light · theme-dark · syntax/
└── i18n/               messages_fr.properties
```

### Les quatre règles tenues dans ce code
1. **Une couleur = une classe CSS.** Aucun `setStyle()` dans le Java (vérifiable par `grep`).
2. **Une règle pédagogique = côté serveur.** L'interface affiche l'état reçu ; elle ne décide jamais d'un droit.
3. **Aucun appel réseau sur le FX Application Thread** : tout passe par `TaskRunner`.
4. **Un libellé = une clé i18n** (`messages_fr.properties`) pour les textes communs.

---

## 5. Agent d'assistance — où il est intégré

### Étape 1 — le contrat d'usage, exécutable

| Livrable | Où |
|---|---|
| Contrat de refus (PDF, 4 p.) | `docs/ia/Note-de-cadrage-agent-IA.pdf` — **15 cas : 11 en position A, 4 en position B** |
| Source unique du contrat | `resources/fr/ihm2026/ia/cas-de-refus.json` |
| Génération du PDF | `ia/build_pdf_cadrage.py` (le PDF est **produit depuis le JSON**) |
| Lecture des libellés par le code | `api/ia/ContratRefus.java` — les messages ne sont **plus** écrits en Java |
| Vérification | `ContratUsageTest` (10 tests) + `ContratRefusTest` (6 tests) |

Le même fichier alimente le document **et** la suite de tests : `ContratUsageTest` rejoue chaque
cas, vérifie que le motif annoncé est bien celui obtenu, et contrôle par réflexion que le test cité
existe réellement. Corriger le document sans le code fait échouer le test ; assouplir un garde-fou
sans le document aussi.

Le contrat a d'ailleurs corrigé le code dès sa première exécution : le cas **R11**
(`INDISPONIBLE`) existait dans le code sans être documenté, et **R08** (« la note de Karim »)
n'était intercepté par aucun motif.

### Les quatre points d'intégration

| Écran | Acteur | Ce que fait l'agent | Ce qu'il ne fait pas |
|---|---|---|---|
| **Cours** | apprenant | répond aux questions pendant la lecture, cite la section | ne remplace pas le cours |
| **Exercice** | apprenant | indice, explication d'erreur, renvoi au cours | ne donne pas la solution |
| **Résultats** | enseignant | synthèse chiffrée de la promotion, **brouillon** à publier | ne publie rien sans confirmation humaine |
| **Comptes** | administrateur | journal des échanges : question, motif, citations, latence | ne note personne |

**Pendant un test de validation, l'assistance est fermée par le service** (`MotifRefus.TEST_EN_COURS`),
pas seulement masquée : `Session.demarrerEvaluation()` est posé à l'ouverture du test et refermé par
le routeur dès qu'on le quitte. Une règle d'interface se contourne (raccourci, appel direct) ;
une règle serveur non. Test : `AideIaTest.assistanceFermeePendantUnTest`.

### Chaîne réellement branchée

```
question de l'apprenant
   → AideIaApi.demander()              api/service/AideIaApi.java
       ├── quota journalier (200/jour)
       ├── GardeFou.verifierDemande()  refus en entrée : note, validation, solution
       ├── ModeleIa.repondre()         ModeleSimule (hors ligne) | ModeleDistant (v0.3)
       ├── GardeFou.filtrer()          refus en sortie : note ou validation glissée par le modèle
       └── journal (question, motif, citations, modèle, latence)
   → réponse citée, ou refus présenté comme une orientation pédagogique
```

**Point à retenir :** l'interdit « l'IA ne note pas » n'est pas porté par le modèle mais par
`GardeFou`, exécuté avant **et** après l'appel. 29 tests adversariaux le vérifient, dont un modèle
volontairement fautif qui tente de mettre `14/20` (`AideIaTest.modeleTricheurNeutralise`).

### Corpus et index vectoriel

| Fichier | Rôle |
|---|---|
| `db/03-corpus-ia.sql` | extrait le corpus des cours publiés, table `ia_fragments` (pgvector) |
| `db/05-corpus-v1.sql` | **corpus v1** : 18 cours / 36 blocs (6 notions × 3 langages), retrait des cours de démonstration remplacés — idempotent, généré |
| `db/04-ia-index.sql` | `rechercher_fragments()` : HNSW + plein texte français + seuil de citation |
| `ia/corpus/construire.py` | nettoyage (rôles, longueur, doublons) et gel du jeu de 60 questions + rapport de couverture |
| `ia/indexer.py` | découpage en fragments de 300-600 caractères, calcul des vecteurs |
| `ia/evaluer.py` | évaluation sur jeu figé → `ia/evaluation/rapport-v1-60.md` |
| `resources/fr/ihm2026/ia/system-v1.md` | prompt système, versionné comme du code |

```bash
cd db && psql -f 01-schema.sql && psql -f 02-seed.sql && psql -f 03-corpus-ia.sql \
        && psql -f 05-corpus-v1.sql && psql -f 04-ia-index.sql
cd ../ia && python3 corpus/construire.py && python3 indexer.py \
        && python3 evaluer.py --jeu evaluation/jeu-de-test-v1-60.jsonl
```

**Index v2 (étape 3).** Après `06-index-v2.sql` :
```bash
cd db && psql -f 06-index-v2.sql
cd ../ia && python3 indexer.py --mode blocs && python3 indexer.py \
        && python3 -m unittest test_decoupage -v \
        && python3 evaluer_etape3.py
```
Le corpus est découpé en fragments de 300 à 600 caractères (25 fragments pour 36 blocs), avec 15 %
de recouvrement et un préfixe de contexte qui sert à retrouver le fragment sans jamais apparaître
dans la citation. Mesuré sur 40 questions **jamais utilisées pour le réglage** : top-5 **100 %**
(v1 : 98 %), témoin dans le top-5 98 % (v1 : 92 %), et surtout **1 fuite hors domaine sur 4 au lieu
de 4** — c'est le seuil de citation qui devient utilisable. Le top-1, lui, stagne autour de 70 % :
c'est le plafond du vecteur de démonstration, écrit noir sur blanc dans le rapport.

**Corpus v1 (étape 2).** Le corpus n'est plus un jeu de démonstration : c'est une grille de
**6 notions** (variables, conditions, boucles, fonctions, collections, erreurs) × **3 langages**,
soit 18 cours et 36 fragments — **12 exactement par langage**, ce qui corrige le déséquilibre
antérieur (5 Python / 3 Java / 3 C++). Les corrigés d'exercices sont exclus par construction
(`role = COURS` seulement) et les doublons sont retirés par empreinte puis par proximité (Jaccard).

**Jeu de 60 questions gelé avant toute mesure** (`ia/evaluation/jeu-de-test-v1-60.jsonl`,
empreinte enregistrée dans `ia/corpus/MANIFESTE.json`). Chaque question porte un **témoin** : le mot
que doit contenir le fragment cité pour que la réponse soit juste. `construire.py` refuse de geler
le jeu si un témoin est absent du cours attendu — et il a effectivement trouvé un manque réel lors
de la première exécution (le cours C++ ne parlait pas du passage par référence).

Mesuré le 27/10/2026 sur 60 questions, seuil 0,05, filtre de langage :
top-1 **73 %** · top-5 **92 %** · témoin dans le top-1 **77 %** · témoin dans le top-5 **87 %** ·
soit **+10 points** de top-1 grâce au filtre de langage. Détail : `ia/evaluation/rapport-v1-60.md`.

| Livrable de l'étape 2 | Où |
|---|---|
| Document de l'étape (PDF, 3 p.) | `docs/ia/Corpus-v1-rapport-de-couverture.pdf` — **généré depuis le corpus et le rapport de mesure** |
| Source du corpus | `ia/corpus/source-corpus.json` (6 notions × 3 langages, 36 blocs `role = COURS`) |
| Corpus construit | `ia/corpus/corpus-v1.json` + `corpus-v1.tsv` (export embarqué dans l'application) |
| Nettoyage et gel | `ia/corpus/construire.py` — 4 passes, puis écriture du jeu de 60 questions et de son empreinte |
| Preuve de gel | `ia/corpus/MANIFESTE.json` (date de gel + SHA-256, dont une signature invariante du jeu) |
| Rapport de couverture | `ia/evaluation/rapport-couverture-v1.md` |
| Mesures | `ia/evaluation/rapport-v1-60.md` |
| **Étape 3 — découpage et indexation** | `db/06-index-v2.sql`, `ia/indexer.py`, `ia/test_decoupage.py` |
| Document de l'étape 3 (PDF, 3 p.) | `docs/ia/Etape-3-decoupage-et-indexation.pdf` |
| Protocole de mesure de l'étape 3 | `ia/evaluer_etape3.py` → `ia/evaluation/rapport-etape3-v2.md` |
| **Étape 4 — stratégie de modèle** | `resources/fr/ihm2026/ia/strategie-modele.json`, `api/ia/PolitiqueModele`, `TransportHttp`, `ModeleDistant` |
| Document de l'étape 4 (PDF, 4 p.) | `docs/ia/Etape-4-strategie-de-modele.pdf` + capture `docs/captures/exercice-ia-desactivee.png` |
| **Étape 5 — prompts et format de sortie** | `ia/prompts/` (gabarit, générateur, prompt v2, schéma), `api/ia/SortieIa` |
| Document de l'étape 5 (PDF, 5 p.) | `docs/ia/Etape-5-prompts-et-format-de-sortie.pdf` |
| **Étape 6 — modèle local entraîné** | `ia/modele/` (entraîneur, négatifs, poids, rapport de mesures), `api/ia/ModeleLocal`, `api/ia/Tokeniseur` |
| Insertion en base | `db/05-corpus-v1.sql` — idempotent, exécuté deux fois sur PostgreSQL 17 + pgvector |

### Étape 4 — stratégie de modèle

| Décision | Où elle est écrite |
|---|---|
| 5 critères pondérés, 3 options notées | `resources/fr/ihm2026/ia/strategie-modele.json` |
| Option retenue : **RAG sur API généraliste** (3,70 / 5), apprentissage supervisé écarté avec motif | même fichier, section `decision` |
| Coût recalculé : **≈ 20,6 $ / mois** à 52 800 requêtes (prix relevés le 27/10/2026) | section `prix` + `volume` |
| Repli : délai 3000 ms, disjoncteur 3 échecs → 120 s, aucune relance | `api/ia/PolitiqueModele.java` (lit le JSON) |
| Fournisseur encapsulé | `api/ia/TransportHttp.java` — seule classe qui lit et envoie la clé |

Trois choses sont **testées**, pas affirmées : la décision est bien l'option de meilleure note
pondérée (`decisionCoherenteAvecLesNotes`), l'inventaire des sorties réseau est fermé — un troisième
fichier qui ouvrirait une connexion fait échouer la suite — et la panne ne bloque rien : les
commandes d'assistance se désactivent, l'éditeur et « Exécuter » continuent de répondre.

```bash
# simuler la panne et voir l'écran de repli (smoketest du poste apprenant)
xvfb-run -a java -Dihm.smoketest=1 -Dihm.captures=docs/captures -cp "target/classes:<classpath>" fr.ihm2026.Launcher --role=apprenant --poste=PC4
```
La clé du fournisseur se pose par variable d'environnement, jamais dans le dépôt :
`set IHM_API_KEY=...` (Windows) ou `export IHM_API_KEY=...`.

### Étape 5 — prompts et format de sortie

Le prompt n'est pas un texte décoratif : c'est **la** deuxième source de vérité de l'agent, avec le
contrat de refus. Il est donc généré, versionné et vérifié.

| Décision | Où elle est écrite |
|---|---|
| Prompt rédigé à la main dans un gabarit, **généré** ensuite | `ia/prompts/gabarit.md` → `ia/prompts/system-v2.md` (7 665 caractères) |
| Les 8 libellés de refus sont **extraits** de `cas-de-refus.json`, jamais recopiés | `ia/prompts/generer_prompt.py` (`table_vocabulaire()`) |
| 5 exemples *few-shot*, dont **2 refus** (`DECISION_DEMANDEE`, `HORS_PERIMETRE`) | `generer_prompt.py` (`exemples()`) |
| Version unique, journalisée à chaque appel | `ModeleSimule.VERSION_PROMPT = "system-v2"` |
| Source unique du prompt : `ia/prompts/` (copiée sur le classpath par le pom, `filtering=false`) | `pom.xml` (ressource `ia/prompts` → `fr/ihm2026/ia/`) |
| Format de sortie : 5 champs, `refus` **est** le motif | `ia/prompts/sortie-schema.json` — vérifié par `api/ia/SortieIa` |
| Ce qui n'est pas conforme est **rejeté**, jamais rafistolé | `SortieIa.lire()` → `Optional.empty()` |

Le format est volontairement pauvre, parce qu'un format riche se contourne :

```json
{ "version": "system-v2",
  "reponse": "trois phrases maximum, en français",
  "citations": [{ "section": "...", "langage": "python|java|cpp" }],
  "notion": "VAR|COND|BOUCLE|FONC|COLL|ERREUR|DIVERS",
  "refus": "AUCUN|HORS_PERIMETRE|SOLUTION_DEMANDEE|DECISION_DEMANDEE|TEST_EN_COURS|PUBLICATION_AUTOMATIQUE|DONNEES_INSUFFISANTES|QUOTA|INDISPONIBLE" }
```

Deux règles font tout le travail : **une réponse cite au moins une section** (sinon elle n'est pas
affichable) et **un refus ne cite rien** (sinon il prétend répondre). `refus` n'est pas un booléen :
avec `refus: true` + `motif: AUCUN`, un modèle pourrait se contredire ; avec un seul champ, non.

Ce que la suite de tests démontre (`FormatSortieTest`, 10 tests) :

1. le prompt **livré** est identique au fichier maître `ia/prompts/system-v2.md` (le pom le copie :
   les deux ne peuvent pas dériver) ;
2. il contient les **8 libellés** du contrat mot pour mot, l'interdiction de noter/valider, la langue
   et l'obligation de citer ;
3. il contient bien **5 exemples, dont 2 refus** ;
4. le schéma et l'énumération Java listent **les mêmes** motifs ;
5. `SortieIa.lire()` rejette : JSON illisible, champ manquant, motif inconnu, langage hors des trois
   parcours, notion inconnue, plus de 3 citations, réponse vide ou > 600 caractères, réponse sans
   citation, refus qui cite ;
6. **10 questions du jeu gelé de l'étape 2** produisent un JSON valide **avec une citation juste** —
   c'est le critère de sortie de l'étape, et il est rejoué à chaque `clean test`.

```bash
# régénérer le prompt et le schéma, puis vérifier qu'ils n'ont pas dérivé
python3 ia/prompts/generer_prompt.py && python3 ia/prompts/generer_prompt.py --verifier
# → prompt system-v2.md (7665 caractères) et sortie-schema.json à jour
```

La preuve du §6 est reproductible : `ia/preuve_sortie.java` rejoue les dix mêmes questions et écrit
`ia/evaluation/preuve-10-questions.json`, que le document de l'étape reprend sans recopie.

```bash
java -cp "target/classes:$(cat /tmp/cp.txt)" ia/preuve_sortie.java
# 10 questions : 10 · justes : 10
```

Un défaut a été trouvé par le test n°6 et corrigé : « Que vaut `7 // 2` en Python ? » était refusé
hors périmètre, parce que le découpage en mots jetait les jetons de moins de trois caractères — donc
les chiffres. Les nombres sont désormais des jetons porteurs de sens (`ModeleSimule.mots()`), ce qui
est le minimum pour un cours de programmation.

### Étape 6 — le modèle local, entraîné et exécuté hors ligne

Jusqu'ici, « le modèle » était un retrouveur de fragments écrit à la main (`ModeleSimule`). Cette
étape **apprend** ce qui était codé en dur — la notion dont parle une question et la décision de
répondre ou de refuser — et l'exécute sans réseau, sans clé et sans dépendance ajoutée.

| Ce qui est appris | Comment | Où |
|---|---|---|
| **Décision** : répondre, refuser une solution, refuser une note, refuser hors parcours (4 classes) | Naive Bayes multinomial, α réglé en validation croisée | `ia/modele/entrainer.py` → candidat `modele-local-v2.json` |
| **Notion** : VAR, COND, BOUCLE, FONC, COLL, ERREUR (6 classes) | même famille, occurrences pondérées par l'idf | même fichier, tête `notion` |
| **Couverture** : la question parle-t-elle du domaine ? | recouvrement pondéré, seuil lu dans le fichier (0,10) | tête `seuil` |
| **Table des sections** | lue du fichier : 3 langages × 6 notions, aucune règle en dur | `sections` |

**Données d'entraînement** : les 60 questions du jeu gelé de l'étape 2 (légitimes), 48 exemples
synthétiques annotés pour ce projet, les demandes du contrat de refus de l'étape 1, 64 négatifs
d'entraînement de `ia/modele/negatifs.json`, et les 36 fragments du corpus comme exemples de soutien.
Les 24 questions synthétiques d'évaluation sont réservées et distinctes des données d'entraînement.
Elles sont générées, pas collectées auprès d'apprenants.
Le fichier `ia/modele/questions-synthetiques-ajustement.jsonl` a servi uniquement aux essais
d'ajustement et n'est pas inclus dans le score du test réservé.

**Mesures** (`ia/modele/rapport-modele-local.md`, régénérable) :

| Mesure | Résultat |
|---|---|
| Candidat v2 : notion, validation croisée laissée-un-dehors | **90/108 = 83 %** |
| Candidat v2 : notion, test synthétique réservé | **14/24 = 58 %** |
| Négatifs de mesure, jamais vus | **23/24 refusés** |
| Questions légitimes refusées à tort (40, dont 24 synthétiques réservées) | **9/40** |
| Section trouvée sur le jeu de 60 | **60/60** — témoin attendu présent dans le fragment choisi : 52/60 (87 %) |

Le candidat v2 n'est **pas activé** : le test synthétique réservé montre une généralisation encore
insuffisante (14/24) et trop de refus de questions légitimes (9/40). L'application continue d'utiliser
le modèle v1. Les exemples synthétiques ne remplacent pas des questions réelles d'apprenants ; il
faudra enrichir et vérifier les annotations avant de promouvoir une nouvelle version.

**Ce que l'étape change dans le code**

1. `ModeleLocal` lit le fichier de poids v1 et répond : décision → notion → section → **phrase du cours**
   (réponse extractive, jamais rédigée). Modèle par défaut : `Config.MODELE=auto` prend le local s'il
   est chargé.
2. `Tokeniseur` : le découpage en mots n'existe plus en deux exemplaires. Les tables (synonymes, mots
   vides) viennent **du fichier du modèle**, donc du Python ; `ModeleSimule` et `ModeleLocal` les
   partagent, et un test compare les découpages mot pour mot (`tokens-reference.json`).
3. La trace journalisée dit qui a répondu : `modele-local@modele-local-v1`, pas un numéro de prompt.

```bash
# régénérer les poids, les mesures et les phrases de référence — puis vérifier qu'ils n'ont pas dérivé
python3 ia/modele/entrainer.py && python3 ia/modele/entrainer.py --verifier
# → génère le candidat modele-local-v2 ; voir les mesures et la décision de promotion dans le rapport

# choisir le modèle à l'exécution (auto/v1 par défaut ; local-v2 est expérimental)
java -Dihm.modele=local   ...   # modèle appris, hors ligne
java -Dihm.modele=local-v2 ...   # candidat v2, réservé aux essais
java -Dihm.modele=simule  ...   # retrouveur de fragments (étape 2), pour comparaison
java -Dihm.modele=distant ...   # fournisseur, exige IHM_API_KEY
```

Ce que la suite démontre (`ModeleLocalTest`, 11 tests) : le modèle actif v1 est **chargé** depuis le fichier
produit par l'entraînement (233 termes, 36 fragments, 3 × 6 sections, 4 classes de décision), le
découpage Java est **le même** que celui du Python sur 15 phrases de référence, dix questions du jeu
gelé obtiennent une **citation juste** dans le bon parcours, les **demandes du contrat** (note,
solution, hors parcours) sont refusées avec le bon motif, les **négatifs de mesure** sont refusés sans
refuser l'utile, la réponse est un **JSON conforme** au format de l'étape 5, et le modèle
**n'ouvre aucune connexion** — un test lit les sources et refuse `java.net`, `Socket`, `HttpClient`
ou `CLE_API` dans `ModeleLocal` et `Tokeniseur`.

**Limites assumées** : le modèle ne rédige pas (la réponse reste extractive) ; il ne traite pas les
refus qui dépendent de l'état de la session (quota, test en cours, indisponibilité) — c'est le
garde-fou qui les décide ; et il ne note pas, ne valide pas, ne débloque pas.
### Direction visuelle macOS (v0.9.0)

Décision d'interface : l'application adopte le langage visuel de macOS Sonoma — **barre de titre
translucide avec pastilles de fenêtre**, surfaces blanches à coins arrondis (10-12 px) posées sur une
fenêtre gris clair, **ombres douces**, couleur d'accent système `#007AFF`, contrôles « push »
dégradés, navigation en **contrôle segmenté** et **palette sombre** complète (`#1E1E1E`).

| Élément | Traitement macOS | Où |
|---|---|---|
| Barre de titre | translucide, séparateur 0,5 px, pastilles **fonctionnelles** (fermer / réduire / agrandir) | `ui/commun/NavBar.java` + `.nav-bar`, `.feu-*` |
| Entrées de navigation | contrôle segmenté, segment actif en accent avec ombre | `.nav-entrees`, `.nav-button.active` |
| Boutons | « push » : dégradé, rayon 7, ombre 3 px ; action principale en accent | `.button`, `.btn-primary` |
| Champs | rayon 6, anneau de focus bleu diffus | `.text-field`, `.combo-box` |
| Cartes et panneaux | rayon 12, bord 0,5 px, ombre 8 px (y +2) | `.card`, `.panel-ia` |
| Tableaux | en-têtes sans fond, lignes séparées de 0,5 px, sélection bleutée | `.table-view` |
| Onglets | *segmented control* (Java / Python / C++) | `.tab-pane` |
| Assistant IA | bulles arrondies asymétriques, question en accent, réponse en indigo 10 % | `.bulle-ia`, `.ia-utilisateur`, `.ia-reponse` |
| Ascenseurs | overlay : piste transparente, pouce fin | `.scroll-bar` |
| Connexion | fond dégradé bleu → violet → rose, carte translucide rayon 20 | `.login-view`, `.login-card` |

**Deux règles rendues exécutables** (`StyleTest`, 5 tests) : les composants de `app.css` ne citent
que des **jetons** de couleur — aucune valeur en dur hors liste blanche assumée (dégradé de connexion,
pastilles, coloration syntaxique) ; et `theme-light.css` / `theme-dark.css` déclarent **exactement les
mêmes 22 jetons**, sinon une bascule d'apparence laisserait un texte sans couleur. Le second test a
d'ailleurs trouvé un défaut réel avant livraison : les couleurs système vivaient dans `app.css`, donc
le thème sombre ne les surchargeait pas ; la palette est désormais entièrement dans les deux fichiers
de thème.

```bash
# captures des deux palettes (smoketest du poste apprenant), sans intervention
java -Dihm.smoketest=1 -Dihm.captures=docs/captures -cp "target/classes:$(cat /tmp/cp.txt)" fr.ihm2026.Launcher --role=apprenant --poste=PC4
java -Dihm.theme=dark -Dihm.smoketest=1 -Dihm.captures=docs/captures/sombre -cp ... fr.ihm2026.Launcher --role=apprenant --poste=PC4
```

Les captures contiennent les écrans autorisés pour le rôle indiqué. L'apparence se bascule à chaud
par le bouton ◐ de la barre, ou se fixe au lancement par `-Dihm.theme=dark`.

### Fenêtre et navigation macOS (v0.10.0)

La v0.9.0 avait la palette de macOS mais gardait la charpente d'une application Windows : barre de
titre du système, navigation horizontale dans cette barre, contenu collé aux bords. Cette version
reprend la **structure** d'une application macOS.

| Ce qui change | Comment | Où |
|---|---|---|
| Fenêtre sans cadre système | `StageStyle.TRANSPARENT`, coins arrondis 12 px, ombre portée 34 px, liseré 0,5 px | `App.java`, `FenetreOutils`, `.fenetre` |
| Barre de titre unifiée | translucide, 46 px, **pastilles actives** (fermer / réduire / agrandir), chevrons **retour / avance**, titre de l'écran, réglage d'apparence | `ui/commun/BarreTitre.java` |
| Navigation | **barre latérale** translucide 244 px : sections « Apprendre » / « Parcours », icônes SVG, ligne active en accent, choix du parcours (Java / Python / C++) avec coche | `ui/commun/BarreLaterale.java` |
| Compte | pied de barre latérale : avatar à initiales, nom, rôle, bouton de sortie | `.compte-lateral` |
| Onglets Java / Python / C++ | contrôle segmenté : seule la surface du segment actif est peinte | `.tab-pane` |
| Polices | **Inter** (repli libre de SF Pro) et **JetBrains Mono**, embarquées dans le jar : rendu identique sous Windows | `core/Fonts.java`, `resources/fr/ihm2026/fonts/` |
| Gestes de fenêtre | glisser la barre pour déplacer, double-clic pour agrandir, 8 zones de redimensionnement avec curseurs | `FenetreOutils` |

Les gestes ne sont pas un détail : une fenêtre sans cadre natif qui ne se déplace pas serait une
régression d'utilisabilité déguisée en progrès visuel. `FenetreOutils` rend donc le déplacement
(glisser la barre), l'agrandissement (double-clic, ou pastille verte) et le redimensionnement
(bords et coins, curseurs `N/S/E/O/NE/NO/SE/SO`), avec des tailles minimales explicites.

```bash
# lancer, ou fixer l'apparence au démarrage
java -cp "target/classes:$(cat /tmp/cp.txt)" fr.ihm2026.Launcher
java -Dihm.theme=dark -cp ... fr.ihm2026.Launcher
```

Captures : `docs/captures/` (clair) et `docs/captures/sombre/` — produites par le smoketest, pas par
une maquette. Structure du code : `NavBar` a disparu au profit de `BarreTitre` + `BarreLaterale`,
parce qu'une barre de navigation horizontale n'existe pas dans une fenêtre macOS.

Deux tests de plus (`StyleTest`, 116 tests au total) : les polices d'interface sont **embarquées et
existent sur le classpath**, et la police principale est `Inter`, jamais `SF Pro` — cette dernière
n'existe pas sous Windows, et une interface dont la police change selon la machine n'est pas une
direction visuelle, c'est un accident.

### Animations macOS (v0.11.0)

macOS n'anime pas pour décorer : il anime pour **expliquer un changement d'état**. Les durées sont
donc courtes, sans rebond, et le mouvement indique toujours la provenance.

| Animation | Durée | Déclencheur | Ce qu'elle explique |
|---|---|---|---|
| Ouverture de fenêtre | 220 ms | lancement | la fenêtre apparaît (fondu + 1,5 % d'échelle) |
| Changement d'écran | 170 ms | navigation | l'écran arrive **du côté d'où l'on vient** : à droite en avançant, à gauche en revenant |
| Apparition d'une bulle d'assistant | 170 ms | question, indice | une réponse vient d'arriver (fondu + 8 px de montée) |
| Bascule d'apparence | 90 + 170 ms | bouton ☀/☾ | le thème change au point le plus sombre de la voile, donc sans clignotement |
| Appui sur un bouton | 90 ms | tout bouton | la cible est bien enfoncée (échelle 0,955) |
| Chevron retour / avance | 90 ms | navigation | le sens du déplacement |
| Repli de la barre latérale | 220 ms | bouton ▤ de la barre de titre | le contenu reprend la place (largeur + opacité) |

**Deux garde-fous**, parce qu'une animation peut aussi être une nuisance :

1. `-Dihm.animations=off` — mouvement réduit. Dans ce cas les transitions ne sont même pas créées :
   les écrans s'affichent directement, et les méthodes d'animation acceptent `null` sans broncher.
2. `Animations.terminerTout()` — le smoketest amène toute animation à son état final **avant** chaque
   capture : aucune image livrée n'est prise au milieu d'un fondu.

**La preuve, et une limite assumée.** Une capture d'écran ne montre pas un mouvement : les images du
smoketest sont donc toutes des états finaux. La démonstration est faite par **mesure**, avec l'instrument
livré `outils/sonde-animations.java`, exécuté au ralenti (×10) :

```
$ xvfb-run -a java -Dihm.animations.echelle=10 -cp … outils/sonde-animations.java
t=400ms  opacité=0,846   en cours=5
t=700ms  opacité=0,757   en cours=1     ← la voile est au plus sombre, le thème est appliqué ici
t=1200ms opacité=0,853   en cours=1
t=2000ms opacité=0,957   en cours=1     ← la scène se découvre
```

Sous Windows, double-cliquer sur `outils\sonde-animations.cmd` compile le projet, prépare le
classpath Maven et lance automatiquement la même sonde. Le script utilise le JDK configuré pour le
projet et garde la fenêtre ouverte pour afficher les mesures.

L'échelle `ihm.animations.echelle` n'est pas un réglage utilisateur : c'est un instrument de mesure,
borné à 50, qui sert à relire les animations image par image.

Un défaut réel a été trouvé par cette sonde et corrigé : le repli de la barre latérale **ne bougeait
pas**. La feuille de style impose une largeur minimale de 244 px, si bien qu'animer la seule largeur
préférée ne produisait aucun mouvement — l'animation « tournait » sans effet visible. La largeur
minimale est désormais levée pendant l'animation, puis rétablie.

Tests : `AnimationTest` (5 tests) fixe les bornes (aucune animation > 250 ms, appui plus court que
changement d'écran), vérifie que la désactivation est totale, que `terminerTout()` est sûr, et que
chaque fichier qui anime est bien branché sur `Animations` — de la même manière que l'inventaire des
sorties réseau de l'étape 4 : un contrat, pas une intention. **121 tests** au total.

### Style « old school » (v0.12.0)

Un second style visuel complet, activable à chaud — et pas un simple filtre de couleur : changer de
style change aussi **les animations**, parce qu'une transition de 1995 ne se fait pas en fondu.

| Élément | Traitement rétro | Où |
|---|---|---|
| Palette | phosphore vert sur noir (`#0B0F0B` / `#B6F0B6`), assistant en ambre comme un Minitel | `retro.css` |
| Typographie | tout en **JetBrains Mono**, titres compris | `retro.css` |
| Formes | aucun arrondi, aucune ombre : boutons **biseautés** dont le biseau s'inverse à l'appui | `retro.css` |
| Ambiance | **balayage cathodique** (1 ligne sur 3, opacité 8–15 %, scintillement) | `ui/commun/Retro.java` |
| Allumage | l'image s'écrase verticalement puis s'ouvre, comme un tube qui démarre | `Retro.allumage()` |
| Changement d'écran | **balayage sec** : une secousse de 24 px et trois scintillements, 160 ms, sans accélération | `Animations.balayer()` |
| Réponse de l'assistant | **machine à écrire** caractère par caractère derrière un curseur plein | `Retro.ecrire()` |
| Attente | **rouet de patience** ⣾ ⣽ ⣻ ⢿ dans le pied du panneau | `Retro.demarrerRouet()` |
| Pastilles de fenêtre | rouges/jaunes/vertes, mais carrées, avec biseau | `retro.css` |

```bash
java -Dihm.style=retro -cp "target/classes:$(cat /tmp/cp.txt)" fr.ihm2026.Launcher
# ou, à l'exécution : bouton ▞ de la barre de titre
```

**Si l'on ne peut pas saisir dans la fenêtre.** Lancé depuis un fichier `.cmd`, le poste s'ouvre
souvent **derrière la console** : la fenêtre n'est pas active, et les touches vont à la console — on
croit que le champ refuse la saisie. Depuis la v0.14.2 : la fenêtre passe devant et prend le focus
au démarrage, un clic n'importe où sur la carte place le curseur dans le champ, et surtout **le
poste se connecte tout seul** : l'adresse du PC1 est déjà renseignée dans son fichier de lancement
(`set "SERVEUR=..."`), donc plus rien à taper. `-Dihm.connexion.auto=false` rétablit l'écran
d'attente.

**Si la fenêtre dépasse l'écran.** La fenêtre était ouverte en 1280 × 800 quelle que soit la
machine. Sur un portable 1366 × 768 réglé en 125 %, la zone utile ne fait que 1092 × 614 pixels
logiques : la barre latérale sortait à gauche, la carte de connexion à droite, et le bouton
« Se connecter » n'était plus atteignable. Depuis la v0.14.1 la taille est **bornée par l'écran**
(`Config.tailleFenetre`, 92 % de la largeur et 90 % de la hauteur utiles, avec un plancher de
lisibilité 720 × 520), le minimum de fenêtre suit la même borne, et la fenêtre est **centrée** sur
la zone utile.

Deux points de méthode :

1. **Le style est une donnée, pas une copie du code.** `retro.css` ne redéfinit que des jetons
   existants — un test le vérifie et refuse tout jeton inventé ; et toutes ses règles descendent de
   `.racine--retro`, donc un seul ajout de classe bascule l'interface entière.
2. **Les animations rétro restent des animations mesurables.** Le balayage, le rouet et la machine à
   écrire passent par `Animations.suivre()` ; `Animations.aTerminer()` enregistre leur état final,
   si bien que `terminerTout()` affiche toujours un texte complet avant une capture. Un écran à
   moitié écrit serait une régression, pas une image.

Captures : `docs/captures/retro/` (12 PNG) — smoketest complet, comme pour les deux autres palettes.

Tests : `StyleTest` (7) et `AnimationTest` (6) couvrent le style rétro — jetons autorisés, absence
d'ombres, branchement du balayage et de l'allumage, durée de 160 ms sans courbe d'accélération, et
passage obligatoire par `Animations.suivre()` / `aTerminer()`. **123 tests** au total.

### Postes en réseau local (v0.13.0)

Jusqu'à la v0.12.0, la plateforme vivait dans une seule fenêtre : on y choisissait son rôle parmi
trois cartes. Une salle de classe ne fonctionne pas comme cela — chaque poste a **un** rôle, et les
postes doivent partager ce qui s'y passe. La v0.13.0 sépare donc les fenêtres et les relie par un
serveur local.

**Qui fait quoi.** Un poste par rôle, un poste serveur, et des fichiers de lancement dédiés :

`DEMARRER.cmd`, à la racine du projet, vérifie que le dossier contient les scripts requis et rappelle
quel fichier lancer pour chaque poste ; il ne propose pas de menu ni de choix de rôle.
`postes\verifier.cmd` affiche un diagnostic si un poste ne démarre pas. Un `.cmd` lancé depuis
l'aperçu d'un zip ne trouvera pas `pom.xml` : extraire d'abord le projet complet.

| Poste | Rôle | Fichier | Ce qui s'ouvre |
|---|---|---|---|
| **PC1** | **serveur** | `postes\1-serveur-PC1.cmd` | une console qui démarre le serveur et son service de découverte LAN |
| **PC2** | administrateur | `postes\2-administrateur-PC2.cmd` | saisie de l'adresse dans la console, puis comptes et rôles |
| **PC3** | enseignant | `postes\3-enseignant-PC3.cmd` | saisie de l'adresse dans la console, puis conditions et résultats |
| **PC4** | apprenant | `postes\4-apprenant-PC4.cmd` | saisie de l'adresse dans la console, puis parcours et exercices |

Le **PC1 est le serveur** : son fichier n'ouvre pas d'interface, il affiche en grand

```
    ADRESSE A SAISIR SUR LES 3 AUTRES POSTES :

              192.168.1.10:7777
```

Cette adresse est lue par `ipconfig` **avant** le démarrage de Java : elle apparaît donc
immédiatement, sans attendre la compilation. Sur PC2, PC3 et PC4, le script demande l'adresse dans
la console. Une adresse vide arrête le lancement ; une adresse valide est transmise à l'application
qui tente la connexion automatiquement. Le script la redemande à chaque lancement.

```bash
# PC1 (serveur) : affiche l'adresse à taper sur les autres postes
postes/1-serveur-PC1.cmd                     # Windows
postes/1-serveur-PC1.sh                      # Linux / macOS
# ou, directement
./mvnw -B javafx:run "-Djavafx.args=--role=serveur --poste=PC1 --port=7777"

# PC4 (apprenant) : --serveur est l'IP affichée par le PC1
./mvnw -B javafx:run "-Djavafx.args=--role=apprenant --poste=PC4 --serveur=192.168.1.10"
```

Les scripts lancent Maven en mode **bavard** (`-B`, et non `-q`) : au premier lancement, Maven se
télécharge puis les dépendances arrivent, ce qui prend plusieurs minutes et doit se voir — une
fenêtre qui n'affiche rien pendant trois minutes passe pour un blocage.

Les options `--role=`, `--poste=`, `--serveur=` et `--port=` sont traduites en propriétés système
par `Launcher`, parce que `javafx:run` relance une seconde JVM et ne transmet pas les `-D` : les
mêmes valeurs fonctionnent donc en ligne de commande et depuis un fichier `.cmd`.

Le parcours complet est vérifiable sans clic humain : `outils/sonde-poste.java` démarre un serveur
de test, lance l'application en poste PC1, **remplit le champ d'adresse et appuie sur « Se
connecter »**, puis lit le titre de la fenêtre obtenue et enregistre une capture.

```bash
# -Dsonde.role / -Dsonde.poste / -Dsonde.port / -Dsonde.capture
# -Dsonde.publier=true     le poste publie une note, comme après un test
# -Dsonde.ecran=Resultats  clique une entrée de la barre latérale
xvfb-run -a java -cp "target/classes:$(cat /tmp/cp.txt)" outils/sonde-poste.java
# [sonde] adresse saisie : 127.0.0.1:7777 → clic sur « Se connecter »
# [sonde] écran obtenu  : « Comptes et rôles — Plateforme d'apprentissage »
# [sonde] capture        : /tmp/poste-PC1.png
```

Si un serveur écoute déjà sur le port, la sonde s'y connecte au lieu d'en démarrer un second : on
peut donc rejouer une démonstration à trois postes (PC1, PC3 qui publie, PC2 qui lit).

Tests : `ReseauTest` (9 tests) démarre un **vrai** serveur sur un port éphémère et s'y connecte en
HTTP — présence, annonce d'un poste, refus d'un rôle inconnu, partage d'un résultat, recalcul de
« validé », déconnexion, décodage d'adresse, et message lisible quand le serveur est éteint.
**132 tests** au total.

### Limites assumées

1. **Vecteurs de démonstration** (plissement de hachage hors ligne) : ils valident la chaîne
   technique, pas la sémantique. Le rapport documente ce défaut : « qui a gagné la coupe du monde ? »
   obtient 0,157 de similitude, au-dessus de certaines questions légitimes. C'est pourquoi le refus
   ne repose **pas** sur le seul seuil.
2. **`ModeleSimule` ne rédige pas** : il retrouve et cite le cours. Choix assumé, qui rend tout le
   reste testable sans clé d'API.
3. **`ModeleDistant` est un emplacement vide** : il renvoie `INDISPONIBLE`, ce qui permet de vérifier
   que l'application reste utilisable sans assistance.

## 6. Vérification automatique des écrans

L'application sait parcourir les écrans du rôle configuré, sans clic, et produire des captures.
Le test démarre un serveur local temporaire ; aucun accès inter-rôles n'est simulé. Exécuter une
fois par rôle pour vérifier toutes les vues :

```bash
mvn -q javafx:run "-Djavafx.args=--role=apprenant --poste=PC4 --smoketest=1 --captures=docs/captures"
```

Sous Linux sans écran (intégration continue), avec le serveur graphique virtuel Xvfb :

```bash
xvfb-run -a java -Dihm.smoketest=1 -Dihm.captures=docs/captures -cp "<classpath>" fr.ihm2026.Launcher --role=apprenant --poste=PC4
```

Le nombre d'écrans construits dépend du rôle sélectionné ; aucune tournée ne change le rôle
configuré du poste.
C'est ce dispositif qui a permis de voir, et donc de corriger, les défauts de la 0.1.4.

### Notes de version

| Version | Corrections |
|---|---|
| 0.1.1 | lancement depuis un IDE (classe `Launcher`), profils JavaFX 21 / 25 selon le JDK |
| 0.1.2 | JDK 25 vérifié de bout en bout, avertissement natif neutralisé |
| 0.1.3 | runtime Liberica déclaré, `lancer.cmd` / `lancer.sh` |
| **0.14.3** | **l'écran de connexion dit sa version** (une capture suffit à savoir quelle version tourne), la carte s'ancre en haut à gauche si la fenêtre dépasse encore l'écran au lieu de sortir à droite, touche **Entrée** valide depuis n'importe où sur la carte, et filet de sécurité au démarrage qui ramène la fenêtre dans la zone utile — **137 tests** |
| **0.14.4** | **le curseur en double flèche au milieu de la fenêtre, et les clics qui ne faisaient rien** : les huit zones de redimensionnement étaient des régions sans taille maximale, étirées par le `StackPane` sur toute la fenêtre — la dernière, le coin bas-droit (curseur `SE_RESIZE`), recouvrait l'écran entier. Elles redeviennent des bandes de 6 px et des coins de 14 px, le redimensionnement ne peut plus sortir la fenêtre de l'écran, et la sonde `outils/sonde-souris.java` prouve au clic réel (`Robot`) que l'interface reçoit l'événement — **137 tests** |
| **0.14.2** | **plus rien à taper sur un poste** : connexion automatique avec l'adresse du fichier de lancement (ou celle retenue), fenêtre qui passe devant et prend le focus, clic n'importe où sur la carte → curseur dans le champ ; `-Dihm.connexion.auto=false` pour revenir à la saisie — **136 tests** |
| **0.14.1** | **la fenêtre tient sur l'écran du poste** : taille bornée par la zone utile (portable 1092 × 614 → fenêtre ≤ 1092 × 614), minimum de fenêtre aligné, centrage sur la zone utile ; `EcranTest` (4 tests) décrit les cas petit portable / écran courant / grand écran — **136 tests** |
| **0.14.0** | **PC1 est le serveur** : son fichier affiche l'adresse IP à donner aux trois autres postes (lue par `ipconfig`, avant Java) ; PC2 administrateur, PC3 enseignant, PC4 apprenant ouvrent chacun un champ d'adresse puis leur interface dédiée ; scripts renumérotés `1-serveur-PC1` … `4-apprenant-PC4`, menu `DEMARRER.cmd` aligné — **132 tests** |
| **0.13.4** | **un seul point d'entrée** : `DEMARRER.cmd` à la racine (menu PC4/PC1/PC2/PC3 + diagnostic), vérification que le dossier est complet, **aucun chemin d'échec ne sort en silence** (message + `pause`), fenêtres qui restent ouvertes après l'arrêt, fichiers `.cmd` en CRLF — **132 tests** |
| **0.13.3** | **diagnostic de lancement** : wrapper bavard (`MVNW_VERBOSE=true`), dossier et Maven affichés avant compilation, et `postes/verifier-pc4.cmd` qui liste en 20 s dossier, `pom.xml`, JDK, Maven, téléchargement du wrapper puis exécute `mvnw.cmd -v` — **132 tests** |
| **0.13.2** | **parcours du poste en un geste** : double-clic sur le script du poste, l'adresse du serveur se saisit dans la fenêtre (champ déjà sélectionné, Entrée pour valider) et l'interface du rôle s'ouvre ; l'adresse est retenue d'un lancement à l'autre, les scripts compilent en mode bavard (`-B`), sonde `outils/sonde-poste.java` — **132 tests** |
| **0.13.0** | **postes en réseau local** : un poste par rôle (PC1 administrateur, PC2 enseignant, PC3 apprenant) et un poste serveur (PC4) qui relie la salle ; chaque poste ouvre sa propre fenêtre avec son rôle fixé, demande l'adresse IPv4 du serveur, et les résultats des tests y sont partagés ; scripts `.cmd`/`.sh` de lancement, `-Dihm.role=`/`--role=`, serveur sans dépendance nouvelle — **132 tests** |
| **0.12.0** | **style rétro « old school »** : palette phosphore + monospace, formes anguleuses et boutons biseautés, balayage cathodique avec scintillement, allumage du tube, changement d'écran par balayage sec de 160 ms, machine à écrire pour les réponses de l'assistant, rouet de patience pendant l'attente ; `-Dihm.style=retro` ou bouton ▞, animations toujours mesurables et terminables — **123 tests** |
| **0.11.0** | **animations macOS** : ouverture de fenêtre, changement d'écran orienté (avant/arrière), apparition des bulles d'assistant, voile de bascule d'apparence, effet d'appui sur tous les boutons, chevrons, repli de la barre latérale ; `-Dihm.animations=off` pour le mouvement réduit ; instrument de mesure `outils/sonde-animations.java` (échelle ×10) — **121 tests** |
| **0.10.0** | **fenêtre et navigation macOS** : fenêtre sans cadre (coins arrondis 12 px, ombre, liseré) avec gestes rendus (déplacer, double-clic agrandir, 8 zones de redimensionnement), barre de titre unifiée translucide à pastilles actives et chevrons retour/avance, **barre latérale** de navigation à sections et icônes SVG, choix du parcours dans la barre latérale, pied de compte à avatar, onglets en contrôle segmenté, polices **Inter** et **JetBrains Mono** embarquées — **116 tests** |
| **0.9.0** | **direction visuelle macOS (UI/UX)** : barre de titre translucide à pastilles fonctionnelles, contrôle segmenté de navigation, boutons « push » dégradés, cartes rayon 12 avec ombres douces, tableaux allégés, onglets segmentés, bulles d'assistant, ascenseurs overlay, connexion en dégradé ; palette clair/sombre en **22 jetons symétriques** (accent `#007AFF`, sombre `#1E1E1E`), `-Dihm.theme=dark` et bascule à chaud — **115 tests** |
| **0.8.0** | **étape 6 livrée — modèle local entraîné** : deux têtes Naive Bayes apprises hors ligne (décision 4 classes, notion 6 classes) + seuil de couverture, poids exportés en JSON et lus par `ModeleLocal` sans dépendance, `Tokeniseur` unique partagé Java/Python et vérifié mot pour mot, modèle local **par défaut** (`-Dihm.modele=local\|simule\|distant`), mesures documentées (notion 85 % en validation croisée, 23/24 refus, 0 faux refus) — **110 tests** |
| **0.7.0** | **étape 5 livrée — prompts et format de sortie** : prompt `system-v2` (7 665 caractères) généré depuis le gabarit avec le vocabulaire du contrat extrait de `cas-de-refus.json` et 5 exemples dont 2 refus, source unique `ia/prompts/` copiée par le pom, schéma de sortie à 5 champs vérifié par `SortieIa` (rejet, jamais rafistolage), 10 questions du jeu gelé avec citation juste — **99 tests** |
| **0.6.0** | **étape 4 livrée — stratégie de modèle** : décision écrite dans `strategie-modele.json` (5 critères pondérés, 3 options, coût recalculé), fournisseur encapsulé dans `TransportHttp`, politique de repli testée (délai, disjoncteur, reprise) et **preuve visuelle** de l'aide désactivée — 89 tests |
| **0.5.0** | **étape 3 livrée — découpage et indexation** : fragments de 300-600 caractères avec 15 % de recouvrement, contexte d'indexation, couverture lexicale, protocole réglage/mesure sur le jeu gelé (top-5 **100 %**, fuites hors domaine 4/4 → 1/4) — 74 tests Java + **8 tests Python** |
| **0.4.0** | **étape 2 livrée — corpus v1** : grille 6 notions × 3 langages (18 cours, 36 fragments, 12 par langage), nettoyage (rôles, longueur, doublons exacts et proches), jeu de 60 questions **gelé avant mesure**, `db/05-corpus-v1.sql` idempotent exécuté sur PostgreSQL 17 + pgvector, application branchée sur le nouveau corpus — **74 tests** |
| **0.3.3** | **contrat de refus v2** : 8 libellés centralisés dans le JSON (le code les lit, les tests les comparent mot pour mot), garantie technique documentée par cas — 70 tests |
| **0.3.2** | **étape 1 complétée** : positionnement B cadré (4 cas, 2 motifs propres), publication verrouillée par `SyntheseIa.publier()` ; 64 tests |
| **0.3.1** | **étape 1 livrée** : contrat d'usage exécutable (11 cas), PDF généré depuis le JSON, `ContratUsageTest` ; 2 trous de garde-fou comblés (R08, R11) — 60 tests |
| **0.3.0** | **agent intégré à la plateforme** : Cours, Exercice, Résultats (synthèse brouillon), Comptes (journal) ; fermeture côté service pendant un test ; 54 tests |
| **0.2.0** | **agent d'assistance fonctionnel** : garde-fous entrée/sortie, corpus indexé pgvector, réponse citée, quotas, journal — 52 tests ; base PostgreSQL exécutée pour de vrai |
| **0.1.5** | **Maven Wrapper** ajouté : Maven n'est plus une dépendance à installer ; `lancer.cmd` bascule sur `mvnw.cmd` |
| **0.1.4** | **panne de démarrage** : `Can't find bundle for base name fr.ihm2026.i18n.messages` — fichier racine `messages.properties` manquant et locale non fixée ; **version affichée** fausse (0.1.0) désormais filtrée depuis le pom et testée ; dates au format américain → `jj/mm/aaaa` ; cartes de rôle de hauteurs inégales ; colonne vide dans le tableau des comptes ; sélecteur de langue inutile côté administrateur ; panneau latéral du parcours étiré sur toute la hauteur |

## 7. Dépannage

### `Error: JavaFX runtime components are missing, and are required to run this application`

Cette erreur n'est pas une erreur de code. Le lanceur de la JVM refuse de démarrer une classe
principale **qui étend `Application`** lorsque les modules JavaFX ne sont pas sur le *chemin de
modules* (`--module-path`) — c'est le cas quand JavaFX est seulement sur le *classpath*, ce que fait
VS Code avec un fichier `argfile`.

**Trois façons de la régler, de la plus simple à la plus complète :**

1. **Lancer par le lanceur neutre** (déjà en place) : `fr.ihm2026.Launcher` n'étend pas
   `Application`, JavaFX s'initialise alors depuis le classpath.
   → dans VS Code, sélectionner la configuration **« Lancer l'application (JavaFX) »**.
2. **Lancer par Maven**, qui place JavaFX sur le chemin de modules : `mvn javafx:run`.
3. **Déclarer un vrai JDK dans VS Code** (`java.configuration.runtimes`) au lieu du JRE embarqué
   dans l'extension Java — voir le commentaire de `.vscode/settings.json`.

> Le message `Unsupported JavaFX configuration: classes were loaded from unnamed module` est un
> simple avertissement : il est normal pour une application **non modulaire** (choix assumé de la
> v0.1, voir §4).

### Configuration de développement vérifiée

Le projet a été compilé et testé sur **deux** toolchains, avec le même résultat (5/5 tests) :

| JDK | JavaFX retenu | Résultat |
|---|---|---|
| Temurin **21.0.13** | 21.0.12 (LTS) | `mvn clean test` → BUILD SUCCESS |
| Temurin **25.0.5** | 25.0.5 (LTS) | `mvn clean test` → BUILD SUCCESS |

**Poste de développement réel** : Windows, **BellSoft Liberica JDK 25 Full**
(`C:\Program Files\BellSoft\LibericaJDK-25-Full`), extension Red Hat pour Java. Deux réglages
appliqués dans `.vscode/settings.json` :

- `java.configuration.runtimes` déclare ce JDK comme runtime par défaut — sinon l'extension compile
  et lance avec **son** JRE embarqué (25.0.4.1) et le JDK de la machine n'est jamais utilisé ;
- le JDK Liberica « Full » embarque JavaFX 25 : le projet n'en dépend pas (JavaFX 25.0.5 vient de
  Maven), mais rien ne casse si les deux sont présents — le classpath du projet a la priorité.

>`RedirectionNotSupported` dans la console PowerShell n'a aucun rapport avec le projet : c'est un
>refus de redirection de flux par le shell (souvent un `2>&1` ou un `>` placé après une cmdlet).
>Aucune commande de ce README n'en a besoin.

**Ce qui a été observé, pas seulement supposé**, avec JDK 25.0.5 + JavaFX 25.0.5, en lançant par
classpath exactement comme le fait VS Code :

```
java -cp <classpath> fr.ihm2026.App        → Error: JavaFX runtime components are missing…
java -cp <classpath> fr.ihm2026.Launcher   → JavaFX démarre (avertissement « unnamed module »),
                                             puis s'arrête sur « Unable to open DISPLAY »
                                             (machine de test sans écran)
```

La seule différence entre les deux lignes est la classe principale : la cause est donc bien le
contrôle du lanceur, et le correctif est bien le lanceur neutre.

### Runtime Java

Le projet et ses scripts de lancement requièrent **Java 25 LTS**. Maven compile en bytecode Java 25
et utilise JavaFX 25.0.5. Définir `JAVA_HOME` sur le JDK 25 avant de lancer un poste Windows.

### Autres messages fréquents

| Message | Cause | Correction |
|---|---|---|
| `release version 25 not supported` | Maven tourne sur un JDK < 25 | installer un JDK 25 LTS et vérifier `mvn -v` |
| `'mvn' n'est pas reconnu` | Maven absent du `PATH` | `./mvnw` ou `lancer.cmd` : le wrapper télécharge Maven, aucune installation |
| fenêtre blanche / pas de CSS | ressources absentes du `target` | `mvn clean javafx:run` |
| `Can't find bundle for base name fr.ihm2026.i18n.messages` | fichier de libellés racine absent, ou locale du poste non couverte | corrigé en 0.1.4 (fichier racine + locale fixée + repli sans exception) ; vérifier que `target/classes/fr/ihm2026/i18n/messages.properties` existe |
| version affichée différente du pom | constante recopiée à la main | la version est filtrée depuis le pom ; `VersionTest` signale toute dérive |

---

## 8. Ce qui vient ensuite

| Version | Contenu | Critère de démonstration |
|---|---|---|
| **v0.2.1** | séparation des écrans en **FXML + contrôleurs** (Scene Builder), écran enseignant de rédaction de cours avec autosave | même application, vue éditée dans Scene Builder |
| **v0.3** | **serveur REST** (Spring Boot ou Javalin) + accès PostgreSQL + authentification | `Config.USE_MOCK = false` : l'application lit la vraie base |
| **v0.4** | journal d'audit, supervision de l'agent IA, tableaux de bord complets | écran admin + journal consultable |
| **v0.5** | `jpackage` (installateur Windows / macOS / Linux), tests TestFX, captures de référence | installation sur un poste vierge |

---

## 9. Documents de conception associés (`docs/`)

| Fichier | Contenu |
|---|---|
| `analyse-besoins-IHM.pdf` | analyse des besoins v1.3 (74 BF, 35 BNF, 39 IHM, 24 RG) |
| `canvas-document-analyse-besoins.pdf` | même contenu, mise en page canvas |
| `Diagrammes-du-projet.pdf` | 7 diagrammes UML |
| `Modeles-de-taches-du-projet-IHM.pdf` | 8 modèles de tâches HTA |
| `Architecture-des-fichiers.pdf` | arborescence cible et règles de rangement |
| `ia/Note-de-cadrage-agent-IA.pdf` | étape 1 : contrat d'usage de l'agent (15 cas, 8 libellés) |
| `ia/Corpus-v1-rapport-de-couverture.pdf` | étape 2 : corpus v1, nettoyage, jeu de 60 questions gelé, mesures |
