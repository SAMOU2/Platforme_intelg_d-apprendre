# Plateforme d’apprentissage de la programmation

Application de bureau pour apprendre **Java, Python et C++ par niveaux**, avec un poste serveur central et trois rôles : administrateur, enseignant et apprenant.

## 1. L’idée de l’application

La plateforme organise l’apprentissage comme une progression :

```text
Cours → Entraînement avec aide IA → Test final sans IA
      → Correction par l’enseignant → Niveau suivant si le test est validé
```

- **L’administrateur** gère les comptes et affecte chaque apprenant à un enseignant.
- **L’enseignant** crée et publie les niveaux, les cours, les exercices et les tests. Il corrige ensuite les tests finaux.
- **L’apprenant** suit les niveaux dans l’ordre, s’entraîne, passe le test final puis consulte le résultat publié par son enseignant.
- **L’IA** aide uniquement pendant l’entraînement. Elle ne corrige pas le test final, ne donne pas la note et ne débloque pas le niveau suivant.

## 2. Architecture

Un seul poste joue le rôle de serveur : **PC1**. Les autres postes sont des clients.

```text
PC2 Administrateur ─┐
PC3 Enseignant     ─┼─ Découverte sur le réseau local + HTTPS ─→ PC1
PC4 Apprenant      ─┘                                             │
                                                                  ├─ Serveur de salle et découverte
                                                                  ├─ Service HTTPS des comptes et des données
                                                                  └─ Docker → PostgreSQL

PC1 → SMTP   : envoi des codes d’activation et de connexion
PC1 → NVIDIA : aide IA pendant l’entraînement uniquement
```

À retenir :

- **PC1 est le seul serveur.** Il contient la base PostgreSQL, vérifie les comptes et les rôles, conserve les niveaux, les copies et les progressions.
- **PC2, PC3 et PC4 sont des clients JavaFX.** Ils n’installent ni Docker ni PostgreSQL et ne reçoivent aucun secret du serveur.
- Le script lancé sur un client fixe l’interface attendue, mais **PC1 revérifie toujours le compte et son rôle**.
- Les clients recherchent automatiquement PC1 sur le même Wi-Fi ou réseau local. Le port HTTPS peut changer à chaque démarrage : il est annoncé par PC1.
- PC1, PC2, PC3 et PC4 sont des **rôles logiques**. Pour un test, un même ordinateur peut ouvrir les deux services PC1 et les trois interfaces clientes.
- Quand PC1 est éteint ou inaccessible, les clients ne peuvent pas se connecter.

Technologies du projet : Java 25, JavaFX, Maven Wrapper, PostgreSQL 16 avec pgvector dans Docker, HTTPS/JSON, SMTP et l’API NVIDIA appelée uniquement par PC1.

## 3. Outils nécessaires sur le PC

### À installer

| Outil | PC1 serveur | PC clients | Utilité |
|---|---:|---:|---|
| Windows | Oui | Oui | Les lanceurs fournis sont des fichiers `.bat` et `.cmd`. |
| JDK 25 LTS complet | Oui | Oui | Compiler et lancer l’application. Définir `JAVA_HOME` ou ajouter son dossier `bin` au `PATH`. |
| Docker Desktop | Oui | Non | Démarre PostgreSQL uniquement sur PC1. |
| Git | Facultatif | Facultatif | Récupérer et mettre à jour le projet ; un ZIP complet convient aussi. |
| VS Code | Facultatif | Facultatif | Lire ou modifier le code et la configuration ; il n’est pas nécessaire pour lancer l’application. |

Il faut aussi :

- une connexion Internet au premier lancement pour télécharger Maven et les dépendances ;
- le même Wi-Fi ou réseau local pour PC1 et les clients lorsqu’on utilise plusieurs ordinateurs ;
- sur PC1, un accès SMTP pour envoyer les codes par e-mail ;
- sur PC1, une clé NVIDIA uniquement si l’aide IA doit être activée.

### À ne pas installer séparément

- **Maven** : le projet fournit `mvnw.cmd`.
- **PostgreSQL** : Docker télécharge et lance l’image prévue sur PC1.
- **JavaFX et les bibliothèques Java** : Maven les télécharge automatiquement.
- **Un modèle IA local ou un GPU** : l’aide IA passe par l’API NVIDIA depuis PC1.

## 4. Comment lancer la plateforme

### 4.1 Première installation de PC1

Cette préparation se fait **une seule fois sur le poste choisi comme PC1**. Si elle est déjà terminée, passer directement à la section 4.2.

1. Récupérer le projet complet et rester à sa racine. Si c’est un ZIP, l’extraire entièrement avant de lancer un script.
2. Installer le JDK 25 et Docker Desktop, puis lancer `postes\verifier.cmd` pour contrôler le JDK, Maven et le dossier du projet.
3. Copier :

   ```text
   postes\configuration-PC1.exemple.cmd
   ```

   sous le nouveau nom :

   ```text
   postes\configuration-PC1.local.cmd
   ```

4. Renseigner uniquement ce fichier local. Dans le modèle, retirer `REM ` devant les lignes `set` à activer, puis remplacer les valeurs d’exemple :
   - le mot de passe de la base Docker ;
   - le serveur SMTP, l’adresse d’envoi et, si le fournisseur l’exige, son identifiant et son mot de passe ;
   - le nom, l’e-mail et un mot de passe d’au moins 12 caractères pour le premier administrateur, uniquement si la base utilisée n’en contient aucun ;
   - la clé NVIDIA, seulement pour activer l’aide IA.
5. Préparer l’identité HTTPS de PC1 une seule fois avec `postes\preparer-identite-reseau-PC1.bat`. Choisir un mot de passe d’autorité d’au moins 12 caractères et le conserver en privé.
6. Dans `configuration-PC1.local.cmd`, activer le mode réseau automatique avec le même mot de passe d’autorité et un autre mot de passe TLS :

   ```bat
   set "IHM_TLS_RESEAU_AUTO=1"
   set "IHM_TLS_CA_KEYSTORE=%~dp0secrets\autorite-reseau.p12"
   set "IHM_TLS_CA_PASSWORD=VOTRE_MOT_DE_PASSE_AUTORITE"
   set "IHM_TLS_PASSWORD=VOTRE_AUTRE_MOT_DE_PASSE_TLS"
   set "IHM_AUTH_PORT=0"
   ```

7. Approuver le certificat public créé dans `postes\secrets\autorite-ihm.crt` :
   - sur PC1 et sur chaque client, lancer `postes\approuver-autorite-client.bat` ;
   - fournir uniquement le fichier `.crt` et vérifier l’empreinte SHA256 affichée avec celle de PC1 ;
   - si `keytool` refuse l’écriture, relancer le script avec les droits administrateur ; il peut demander le mot de passe du magasin Java (`changeit` par défaut s’il n’a jamais été modifié).

> Ne jamais copier `configuration-PC1.local.cmd` ni un fichier `.p12` sur un client. Seul le certificat public `.crt` peut être transmis.

### 4.2 Démarrage normal

#### Sur PC1

1. Connecter PC1 au Wi-Fi ou au réseau local utilisé par les clients.
2. Double-cliquer sur `postes\authentification-PC1.bat`.
3. Attendre le démarrage de Docker/PostgreSQL puis le message indiquant que l’API HTTPS est prête.
4. Double-cliquer sur `postes\1-serveur-PC1.bat`.
5. **Laisser les deux fenêtres PC1 ouvertes.** Les fermer arrête la plateforme.

#### Sur chaque client

Lors de la première utilisation d’un nouveau client, approuver d’abord le certificat public de PC1 comme à l’étape 7 ci-dessus. Ensuite, lancer uniquement le script du rôle du poste :

| Rôle | Script à lancer |
|---|---|
| Administrateur | `postes\2-administrateur-PC2.bat` |
| Enseignant | `postes\3-enseignant-PC3.bat` |
| Apprenant | `postes\4-apprenant-PC4.bat` |

La plateforme de PC1 doit être découverte automatiquement dans l’écran de connexion. Au premier lancement, Maven et les dépendances se téléchargent : l’ouverture peut prendre quelques minutes.

Pour tester sur **un seul ordinateur**, suivre exactement le même ordre : lancer les deux services PC1, puis ouvrir les scripts administrateur, enseignant et apprenant dont on a besoin.

> `DEMARRER.cmd` affiche seulement la liste des scripts. Il ne lance pas l’application.

### 4.3 Arrêt

1. Fermer les fenêtres clientes.
2. Dans les deux fenêtres de console PC1, appuyer sur `Ctrl+C`.

### 4.4 Si le lancement échoue

- **Aucune fenêtre ne s’ouvre** : lancer `postes\verifier.cmd` et corriger le JDK ou le dossier indiqué.
- **PC1 annonce une configuration absente** : vérifier `postes\configuration-PC1.local.cmd` sur PC1 uniquement.
- **Le client ne trouve pas la plateforme** : vérifier que les deux fenêtres PC1 sont toujours ouvertes et que tous les postes utilisent le même réseau. Ne pas saisir `127.0.0.1` sur un autre ordinateur.
- **Une erreur s’affiche** : lire le premier message `[ERREUR]`. Ne pas supprimer le volume Docker ni la configuration privée pour “réessayer”.
