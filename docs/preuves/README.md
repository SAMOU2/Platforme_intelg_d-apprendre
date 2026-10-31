# Vérification du déploiement centralisé — 31 octobre 2026

## Résultats réellement obtenus

`python3 outils/tests/test_deploiement_central.py` : **12 contrôles réussis**.
Le journal brut est `tests-deploiement.txt` et son rendu PNG est `tests-deploiement.png`.
Ces contrôles portent sur la syntaxe Bash, les contrats de configuration dans les fichiers,
et le refus réel du lanceur HTTPS Linux lorsque sa configuration est absente.
Ce ne sont PAS des tests de connexion Java/HTTPS ni une validation de Windows.

`architecture-serveur-central.png` est un **schéma explicatif**, pas une capture de l'application.
Les PNG sont générés par `outils/preuves_deploiement.py` (Pillow requis).

## Mise à jour interface et Windows

- Formulaire dans un ScrollPane : l'inscription reste défilable sur petit écran.
- Actions placées après les champs, libellés associés et identifiants de contrôle stables.
- Fichiers `1-serveur-PC1.bat`, `2-administrateur-PC2.bat`,
  `3-enseignant-PC3.bat`, `4-apprenant-PC4.bat` : délégation aux `.cmd` du rôle fixé,
  avec propagation du code retour. Les `.cmd` clients retournent 1 sur échec Maven.
- `authentification-PC1.bat` : affiche chaque nom de variable absent (pas sa valeur),
  vérifie la présence du magasin TLS avant compilation et conserve les logs Maven visibles.
- `DEMARRER.cmd` explique les deux services nécessaires sur PC1 et l'absence de Docker côté client.
- `syntaxe-java.txt` : analyse Tree-sitter de 104 fichiers, sans erreur syntaxique détectée.
  Ce n'est pas une compilation. Reproduction : installer `tree-sitter` et `tree-sitter-java`,
  parser tous les fichiers `src/**/*.java` avec `tree_sitter_java.language()` et vérifier
  `root_node.has_error` pour chacun.

Les assertions sur `.bat`/`.cmd` sont des contrôles de contenu : ces fichiers n'ont
pas été exécutés sous Windows dans cet environnement Linux. Pour valider sur PC :
1. Double-cliquer le `.bat` du rôle et vérifier l'ouverture de la bonne interface.
2. Avec un mauvais JDK, vérifier que l'erreur reste affichée.
3. Sur PC1, sans configuration SMTP/TLS, vérifier les noms manquants.
4. Avec Maven en échec, vérifier `echo %errorlevel%` après `call postes\4-apprenant-PC4.bat` : 1 attendu.
5. Tester l'inscription avec une fenêtre de 720 × 520 et vérifier le défilement jusqu'au bouton.

## Ce qui n'a pas pu être exécuté ici

- Compilation et tests JUnit : aucun JDK installé ; récupération du JDK GitHub bloquée
  au téléchargement de l'archive ; dépôt Maven également inaccessible.
- Docker/PostgreSQL : Docker absent.
- Captures JavaFX, e-mails SMTP et essai réel entre plusieurs PC : non réalisés.

Des tests JUnit ont été ajoutés pour la sélection de l'hôte, les diagnostics du service
et les codes de connexion ; leur succès n'est pas affirmé dans ce rapport.

## Utilisation dans la salle

1. Sur **PC1 uniquement**, lancer Docker puis `docker compose -f db/docker-compose.yml up -d`.
   PostgreSQL est publié sur `127.0.0.1:5433`, pas sur le réseau des clients.
2. Configurer les variables PostgreSQL/TLS/SMTP sur PC1, puis ouvrir
   `postes\authentification-PC1.bat` et `postes\1-serveur-PC1.cmd`.
   Sous Linux : `bash postes/authentification-PC1.sh` et
   `bash postes/1-serveur-PC1.sh`, dans deux terminaux.
3. Sur les clients : ne pas installer ni lancer Docker ou PostgreSQL.
   Installer un JDK 25, puis ouvrir le lanceur du rôle concerné.
4. Saisir l'IP de PC1 (exemple `192.168.1.10`) dans le nouveau champ serveur.
   Laisser vide pour la découverte UDP. Ne pas inclure `http://` ni le port.
   Pour préremplir le champ, définir `IHM_SERVEUR` avant de lancer le script :
   Windows CMD : `set "IHM_SERVEUR=192.168.1.10"` ; Linux :
   `export IHM_SERVEUR=192.168.1.10`.
5. Autoriser 7777 TCP/UDP et 8443 TCP sur PC1 ; faire approuver le certificat
   par les JDK clients, avec SAN correspondant à l'IP/nom utilisé.
   Ne jamais distribuer la clé privée TLS ni les secrets PostgreSQL/SMTP aux clients.

Sur une base Docker existante, appliquer les migrations 07, 08 et 09 manuellement
après sauvegarde. Ne pas supprimer le volume de données.

## Validation à effectuer avec JDK 25 et base de test dédiée

- `mvnw.cmd test` (Windows) ou `bash mvnw test` (Linux).
- Pour les tests PostgreSQL, définir IHM_DB_URL, IHM_DB_USER, IHM_DB_PASSWORD vers
  une base de test migrée (01, 02, 07, 08, 09). Sans URL ils sont ignorés.
- Vérifier la connexion des trois rôles au même PC1 avec Docker arrêté sur les clients.
- Vérifier un serveur absent, un mauvais certificat et un mauvais code.
- Capturer les véritables fenêtres avec le dispositif SmokeTest une fois l'environnement prêt.

## Périmètre

Cette mise à jour centralise la configuration de connexion. Elle ne change pas le parcours
PIN/inscription : le PIN préalable reste requis. Le parcours proposé « demande sans PIN,
puis PIN après acceptation » reste à définir et implémenter séparément.
Les données pédagogiques restent simulées comme indiqué dans le README principal.
