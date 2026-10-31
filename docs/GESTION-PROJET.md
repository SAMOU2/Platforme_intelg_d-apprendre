# Gestion du projet

Ce fichier est le registre central des demandes, du découpage, des responsables, des dépendances
et des preuves de validation. Toute nouvelle demande y est inscrite avant de commencer le travail.
Les responsables sont pour le moment des rôles : l'équipe peut remplacer ces rôles par des noms.

**Dernière mise à jour :** 2026-10-28.

## État de référence du projet

Ces constats servent de base au backlog et évitent de considérer des démonstrations comme des
fonctions de production :

- La connexion réseau actuelle annonce le poste et son rôle configuré ; elle n'authentifie pas une
  personne.
- Les données de comptes sont simulées (`USE_MOCK`) et les modifications restent en mémoire.
- La compilation Maven, JavaFX et les scripts de lancement ciblent maintenant Java 25 LTS.
- Les scripts Windows partagés ont été vérifiés pour CRLF ; le lanceur principal a été exécuté.
  Le démarrage complet de chacun des postes n'a pas été vérifié.

## Tableau de suivi

| ID | Tâche | Priorité | État | Responsable | Dépendances |
|---|---|---:|---|---|---|
| IHM-001 | Définir le contrat d'authentification personnelle | P0 | Terminé | Responsable sécurité / produit | — |
| IHM-002 | Persister les comptes et les mots de passe hachés | P1 | Terminé | Développement données | IHM-001 |
| IHM-003 | Relier création, activation et désactivation au stockage | P1 | À faire | Développement comptes | IHM-002, IHM-004 |
| IHM-004 | Authentifier les personnes côté serveur et créer une session | P0 | En cours | Développement serveur | IHM-001, IHM-002 |
| IHM-005 | Ajouter le formulaire de connexion personnelle au client | P0 | À faire | Développement interface | IHM-004 |
| IHM-006 | Vérifier les autorisations de chaque route serveur | P0 | À faire | Développement serveur | IHM-004 |
| IHM-007 | Tester le parcours complet d'authentification et les refus d'accès | P0 | À faire | Qualité / tests | IHM-003, IHM-005, IHM-006 |
| IHM-008 | Aligner la compilation et les scripts sur le runtime Java 25 LTS | P1 | En cours | Responsable Java / livraison | — |
| IHM-009 | Vérifier le cycle de déconnexion et la fermeture de fenêtre | P1 | Terminé | Développement client | — |
| IHM-010 | Ajouter l'activation et la désactivation dans l'administration de démonstration | P1 | Terminé | Développement comptes | — |
| IHM-011 | Fiabiliser et vérifier les lanceurs Windows | P1 | Terminé | Responsable livraison | — |
| IHM-012 | Vérifier les scénarios d'échec et le lancement complet des postes Windows | P1 | À faire | Qualité / livraison | IHM-011 |
| IHM-013 | Enrichir et valider le modèle local sur des questions inédites | P1 | En cours | Données IA / qualité | — |

**Tâche en cours : IHM-004.** Sur décision de l'utilisateur, l'authentification sécurisée côté
serveur précède IHM-003. La gestion persistante des comptes dépend maintenant de cette API
authentifiée ; elle ne doit pas être exposée directement à PostgreSQL ni au serveur HTTP de salle.
IHM-001 est clôturée avec le contrat décrit dans
[CONTRAT-AUTHENTIFICATION.md](CONTRAT-AUTHENTIFICATION.md). Le choix de transport HTTPS avec
certificat approuvé par chaque poste a été confirmé par l'utilisateur.

## Fiches de tâches à faire

### IHM-001 — Définir le contrat d'authentification personnelle

- **Objectif :** définir comment une personne prouve son identité, et comment l'application établit
  son rôle, sans se fier aux arguments `--role` du poste.
- **Priorité / état :** P0 / Terminé.
- **Responsable :** responsable sécurité / produit.
- **Découpage réalisé :** transport sécurisé, preuve d'identité, mots de passe, sessions,
  limitation d'essais, règles d'autorisation et cas de test documentés.
- **Critères d'acceptation :** le contrat précise les données échangées, le cycle de vie de session,
  les erreurs, les règles de sécurité et les cas de test ; il ne traite jamais le rôle local du poste
  comme une preuve d'identité.
- **Validation :** le document [CONTRAT-AUTHENTIFICATION.md](CONTRAT-AUTHENTIFICATION.md) est créé ;
  le transport HTTPS avec certificat approuvé sur chaque poste a été confirmé par l'utilisateur.
- **Limite :** les autres paramètres documentés (durées, seuils et politiques) sont des valeurs de
  départ à valider avec le responsable de l'application avant déploiement.

### IHM-002 — Persister les comptes et les mots de passe hachés

- **Objectif :** disposer de comptes durables et utilisables par l'authentification réelle.
- **Priorité / état :** P1 / Terminé.
- **Responsable :** développement données.
- **Dépendances :** IHM-001 pour le modèle de compte et les règles de credentials.
- **Découpage :**
  1. Vérifier le schéma PostgreSQL existant et noter les migrations nécessaires.
  2. Implémenter le dépôt de comptes persistant.
  3. Stocker uniquement des mots de passe hachés avec un algorithme adapté, jamais en clair.
  4. Tester la création, la lecture, les doublons et la conservation après redémarrage.
- **Critères d'acceptation :** les comptes survivent au redémarrage ; les e-mails dupliqués sont
  refusés ; aucun mot de passe en clair n'est écrit ; les migrations et tests passent.
- **Avancement :** dépôt JDBC, configuration de connexion par variables d'environnement, création
  utilisant bcrypt/pgcrypto, migration SQL des secrets existants et unicité d'e-mail sans casse
  implémentés.
- **Validation :** `mvnw.cmd -B clean test` — 151 tests réussis, sans échec, avec le test
  d'intégration PostgreSQL actif ; création, hachage bcrypt, refus d'e-mail dupliqué et relecture
  depuis une nouvelle connexion vérifiés. Un compte témoin a également survécu au redémarrage du
  conteneur PostgreSQL. `07-auth.sql` a été exécuté pendant l'initialisation ; les 7 comptes ont un
  hachage bcrypt et l'index e-mail insensible à la casse existe.
- **Limite :** la suite complète active le test PostgreSQL lorsque `IHM_DB_URL` est défini ; sans
  cette variable, le test d'intégration est ignoré. Le port Docker par défaut est 5433 pour éviter
  le PostgreSQL 18 local sur 5432.

### IHM-003 — Relier création, activation et désactivation au stockage

- **Objectif :** faire agir l'administration sur les comptes réels, et non uniquement sur la liste
  en mémoire du mock.
- **Priorité / état :** P1 / À faire (après IHM-004).
- **Responsable :** développement comptes.
- **Dépendances :** IHM-002 et IHM-004 ; toutes les mutations doivent passer par l'API authentifiée.
- **Découpage :**
  1. Ajouter les opérations de gestion de comptes dans l'API d'administration.
  2. Brancher création, activation et désactivation de l'interface sur cette API.
  3. Afficher explicitement les erreurs et rafraîchir la liste après succès.
  4. Tester les doublons, les erreurs réseau, la conservation de l'historique et la protection du
     dernier administrateur actif.
- **Critères d'acceptation :** les changements sont visibles après reconnexion et redémarrage ;
  l'interface ne présente pas une mutation échouée comme réussie ; les résultats associés restent
  conservés lors d'une désactivation.
- **Validation / limites :** à consigner à la clôture.

### IHM-004 — Authentifier les personnes côté serveur et créer une session

- **Objectif :** permettre au serveur de vérifier les identifiants et d'émettre une identité de
  session fiable.
- **Priorité / état :** P0 / En cours.
- **Responsable :** développement serveur.
- **Dépendances :** IHM-001 et IHM-002.
- **Découpage :**
  1. Implémenter la route de connexion conforme au contrat.
  2. Vérifier les credentials sans exposer si un compte ou un mot de passe est incorrect.
  3. Refuser les comptes désactivés et retourner le rôle enregistré côté serveur.
  4. Implémenter déconnexion, expiration et invalidation de session.
- **Critères d'acceptation :** credentials invalides, compte désactivé et session expirée sont
  refusés ; une connexion valide renvoie une session limitée dans le temps et le rôle associé au
  compte persistant, jamais un rôle déclaré par le client.
- **Avancement :** migration des sessions, service PostgreSQL de vérification des credentials,
  limitation d'essais, jetons opaques et routes de connexion/session/déconnexion implémentés.
  Serveur dédié HTTPS sans repli vers HTTP ; procédure de configuration décrite dans
  [README-db.md](../db/README-db.md).
- **Validation :** migration `08-auth-sessions.sql` exécutée deux fois sur la base de développement ;
  les 3 tests d'intégration PostgreSQL d'authentification passent, ainsi que
  `mvnw.cmd -B clean test` (154 tests, aucun échec).
- **Limites / suite :** le serveur HTTPS n'est pas encore relié au client JavaFX ni aux routes
  métier ; aucun certificat TLS n'est configuré ou testé ici. Ajouter une validation HTTP(S)
  directe et vérifier expiration/invalidation lors des prochaines étapes avant clôture.

### IHM-005 — Ajouter le formulaire de connexion personnelle au client

- **Objectif :** compléter la connexion actuelle au serveur par l'identification de la personne.
- **Priorité / état :** P0 / À faire.
- **Responsable :** développement interface.
- **Dépendances :** IHM-004.
- **Découpage :**
  1. Ajouter les champs et états de chargement/erreur au parcours de connexion.
  2. Transmettre les credentials via le canal défini par IHM-001.
  3. Conserver la session sans conserver ni journaliser le mot de passe.
  4. Révoquer la session sur déconnexion et fermeture de l'application.
- **Critères d'acceptation :** aucun écran métier n'est accessible avant authentification ; le rôle
  affiché vient du serveur ; les erreurs restent compréhensibles ; le secret n'apparaît ni dans les
  logs ni dans les propriétés de lancement.
- **Validation / limites :** à consigner à la clôture.

### IHM-006 — Vérifier les autorisations de chaque route serveur

- **Objectif :** protéger les données et opérations même si un client contourne son interface.
- **Priorité / état :** P0 / À faire.
- **Responsable :** développement serveur.
- **Dépendances :** IHM-004.
- **Découpage :**
  1. Inventorier les routes et leurs rôles permis.
  2. Appliquer le contrôle de session et de rôle à chaque route protégée.
  3. Refuser par défaut toute route non explicitement autorisée.
  4. Ajouter des tests directs HTTP avec rôles autorisés, interdits et sessions absentes.
- **Critères d'acceptation :** un rôle apprenant ne peut appeler une opération administrateur en
  envoyant directement une requête ; les appels non authentifiés sont refusés ; les accès permis
  fonctionnent toujours.
- **Validation / limites :** à consigner à la clôture.

### IHM-007 — Tester le parcours complet d'authentification et les refus d'accès

- **Objectif :** prouver que le client, le serveur et le stockage appliquent ensemble les mêmes
  règles.
- **Priorité / état :** P0 / À faire.
- **Responsable :** qualité / tests.
- **Dépendances :** IHM-003, IHM-005 et IHM-006.
- **Découpage :**
  1. Tester connexion valide et rôle attribué par le serveur.
  2. Tester mauvais mot de passe, compte désactivé et session expirée.
  3. Tester l'accès interdit en appelant directement une route.
  4. Tester déconnexion et révocation, puis exécuter toute la suite.
- **Critères d'acceptation :** chaque cas de refus et de succès a un test reproductible ; aucune route
  protégée ne reste accessible après révocation ; la suite complète passe.
- **Validation / limites :** à consigner à la clôture.

### IHM-008 — Aligner la compilation et les scripts sur le runtime Java 25 LTS

- **Objectif :** faire utiliser et annoncer Java 25 LTS par l'environnement de construction et les
  lanceurs, tout en prenant une décision explicite sur la compatibilité bytecode.
- **Priorité / état :** P1 / En cours.
- **Responsable :** responsable Java / livraison.
- **Découpage :**
  1. Java 25 est confirmé comme runtime et niveau bytecode requis.
  2. Aligner la compilation et les lanceurs Windows sur Java 25 LTS.
  3. Mettre à jour les instructions de compilation, de lancement et d'authentification.
  4. Vérifier le build et les tests sur JDK 25; le démarrage graphique reste à confirmer.
- **Critères d'acceptation :** Maven et l'application sont lancés avec le JDK 25 LTS ; le niveau
  bytecode et les versions minimales prises en charge sont documentés et cohérents ; les scripts
  annoncent les mêmes prérequis ; build, tests et lancement JavaFX passent.
- **Validation / limites :** `mvnw clean test` avec Liberica JDK 25.0.2 : BUILD SUCCESS,
  159 tests, 0 échec, 6 tests d'intégration PostgreSQL ignorés faute de base de test. Le
  démarrage JavaFX interactif n'a pas été vérifié dans cette session.

## Tâches réalisées et preuves

### IHM-009 — Vérifier le cycle de déconnexion et la fermeture de fenêtre

- **Objectif :** effacer l'état local et prévenir le serveur lors d'une déconnexion ou fermeture.
- **Priorité / état :** P1 / Terminé.
- **Responsable :** développement client.
- **Critères d'acceptation :** la session locale et le résultat courant sont effacés ; le serveur est
  prévenu si disponible ; une panne du serveur ne bloque pas la déconnexion locale.
- **Validation :** test dédié de déconnexion et suite complète ; 144 tests réussis au moment du
  changement.
- **Limite :** la déconnexion ne révoque pas encore une session d'identité utilisateur, car
  l'authentification personnelle n'est pas implémentée (voir IHM-004).

### IHM-010 — Ajouter l'activation et la désactivation dans l'administration de démonstration

- **Objectif :** permettre les deux actions, avec confirmation, et protéger le dernier administrateur
  actif.
- **Priorité / état :** P1 / Terminé.
- **Responsable :** développement comptes.
- **Critères d'acceptation :** actions activées selon l'état de la ligne, confirmation visible,
  changement reflété dans la table, dernier administrateur protégé.
- **Validation :** tests ciblés de création, activation et protection du dernier administrateur ;
  suite complète : 144 tests réussis.
- **Limite :** le stockage est un mock en mémoire ; les changements disparaissent au redémarrage
  (voir IHM-002 et IHM-003).

### IHM-011 — Fiabiliser et vérifier les lanceurs Windows

- **Objectif :** détecter les prérequis présents et fournir un point d'entrée général compréhensible.
- **Priorité / état :** P1 / Terminé.
- **Responsable :** responsable livraison.
- **Critères d'acceptation :** les scripts modifiés utilisent les fins de ligne CRLF ; le script
  commun détecte le JDK et le wrapper présents sur le poste ; le lanceur général affiche les scripts
  de poste et n'offre pas de choix de rôle.
- **Validation :** `environnement.cmd` a détecté Liberica JDK 25.0.2 et le Maven Wrapper ;
  `DEMARRER.cmd` a été exécuté et affiche les scripts de poste sans menu de rôle.
- **Limite :** le démarrage complet de l'interface JavaFX par chacun des scripts de poste n'a pas
  été vérifié. Les chemins d'échec (JDK absent/ancien, Maven indisponible, adresse vide) ne sont pas
  encore testés : voir IHM-012.

### IHM-012 — Vérifier les scénarios d'échec et le lancement complet des postes Windows

- **Objectif :** confirmer que les scripts de rôle démarrent correctement et restent explicites
  lorsque les prérequis ou l'adresse serveur manquent.
- **Priorité / état :** P1 / À faire.
- **Responsable :** qualité / livraison.
- **Dépendances :** IHM-011.
- **Découpage :**
  1. Tester le chemin nominal du serveur et d'au moins un poste client jusqu'à l'interface.
  2. Tester une adresse vide, un JDK ancien/absent et l'échec du wrapper Maven.
  3. Vérifier que chaque échec affiche la cause et ne laisse pas croire que le lancement a réussi.
  4. Consigner les commandes et résultats afin de rendre les vérifications reproductibles.
- **Critères d'acceptation :** le poste serveur écoute et annonce une adresse ; un poste client
  valide se connecte ; les scénarios d'échec affichent une explication et la console reste lisible ;
  les résultats sont consignés ici.
- **Validation / limites :** à consigner à la clôture.

## Règles d'utilisation

- **Priorités :** P0 = sécurité ou blocage majeur ; P1 = nécessaire à une livraison fiable ;
  P2 = amélioration importante ; P3 = amélioration ultérieure.
- **États :** À faire, En cours, Bloqué, Terminé. Une tâche bloquée nomme explicitement son
  prérequis ou la décision attendue.
- **Responsables :** remplacer le rôle par le nom de la personne lorsqu'une tâche est prise.
- **Dépendances :** ne démarrer une tâche que lorsque ses dépendances sont terminées, sauf travail
  d'analyse explicitement indépendant.
- **Clôture :** ne passer à Terminé qu'après vérification des critères et ajout de la preuve dans
  cette fiche.

### Modèle pour une nouvelle tâche

```text
ID / titre :
Objectif observable :
Priorité / état :
Responsable :
Dépendances :
Découpage en étapes courtes :
Critères d'acceptation :
Validation exécutée / limites connues :
```

## Journal de suivi

- **2026-10-28 :** rapprochement du backlog avec les changements et vérifications déjà effectués.
  IHM-009 à IHM-011 restent clôturées avec leurs preuves et limites consignées ; IHM-012 a été
  créée pour les vérifications de lancement et de scénarios d'échec qui restent à faire. À cette
  date, IHM-001 était identifiée comme prochaine réalisation ; le statut a depuis été actualisé.
- **2026-10-28 :** contrat d'authentification créé dans
  [CONTRAT-AUTHENTIFICATION.md](CONTRAT-AUTHENTIFICATION.md), avec HTTPS confirmé par l'utilisateur.
  IHM-001 est terminée ; IHM-002 est commencée. Les vérifications effectives de bcrypt et de
  persistance restent dans la portée d'IHM-002.
- **2026-10-28 :** première implémentation d'IHM-002 ajoutée (configuration DB non secrète, dépôt
  JDBC, hachage bcrypt côté PostgreSQL, seed et migration). La suite Maven complète passe : 148
  tests. La validation sur une vraie base reste requise ; IHM-002 reste donc En cours.
- **2026-10-28 :** validation renforcée des secrets DB (espaces conservés) et de la limite bcrypt
  (72 octets UTF-8, comptage des caractères Unicode) et précontrôle des e-mails dupliqués dans la
  migration. La suite Maven propre passe : 150 tests. Le daemon Docker local n'est pas disponible ;
  les tests d'intégration PostgreSQL restent à exécuter avant clôture.
- **2026-10-28 :** Docker Desktop démarré ; découverte d'un PostgreSQL 18 local qui occupait le
  port 5432. Le service Compose utilise désormais le port hôte 5433 par défaut
  (`IHM_DB_PORT` personnalisable). La migration, l'intégration compte/bcrypt/doublon et la
  conservation après redémarrage ont été vérifiées ; `mvnw.cmd -B clean test` passe avec 151 tests.
  IHM-002 est clôturée ; IHM-003 est la prochaine tâche.
- **2026-10-28 :** selon l'ordre choisi par l'utilisateur, IHM-004 passe En cours avant IHM-003.
  Migration idempotente des sessions appliquée à la base existante ; tests PostgreSQL d'authentification
  et suite Maven complète validés (155 tests). Le type UUID des sessions, révélé par l'intégration,
  a été corrigé. Le test HTTP utilise une adresse IPv4 explicite et vérifie que le handler refuse
  le transport non chiffré avec `426`. Les limites TLS et l'absence de liaison au client sont
  consignées ; IHM-003 attend l'API authentifiée.
- **2026-10-28 :** entraînement d'un candidat de modèle local v2 avec 48 questions synthétiques
  annotées et jeu réservé de 24 questions synthétiques. Le candidat obtient 14/24 (58 %) sur le test
  réservé et refuse à tort 9/40 questions légitimes. Il est intégré comme choix expérimental
  explicite (`-Dihm.modele=local-v2`) et trois lanceurs de test dédiés sont ajoutés ; les lanceurs
  usuels conservent le modèle v1. Les 11 tests ciblés du modèle passent avec v2 sélectionné, ainsi
  que la suite complète (155 tests) sous v1 et sous v2. Pour clôturer IHM-013, collecter des
  questions réelles anonymisées, les annoter, puis réévaluer sur un jeu indépendant avant toute
  promotion.

## Définition de terminé

Une tâche est terminée lorsque le résultat demandé est implémenté dans son périmètre, que les
critères d'acceptation sont vérifiés, que les tests concernés passent (ou qu'une vérification de
livrable est faite pour une tâche sans code), que la documentation correspond au comportement réel,
et que les limites restantes sont consignées ici. Aucun statut « Terminé » ne doit reposer seulement
sur le fait que le code a été modifié.
