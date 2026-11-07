# Port HTTPS automatique et instance unique sur PC1

## Fonctionnement

- Par défaut, `IHM_AUTH_PORT=0` : Windows choisit et réserve un port libre au moment du bind
  HTTPS. Aucun sondage de port suivi d'une fermeture/réouverture sujette à concurrence.
- Si votre configuration privée contient encore `IHM_AUTH_PORT=8443`, il est traité comme
  port préféré : si occupé, repli sur un port libre. Aucun processus tiers n'est arrêté.
- Un verrou de fichier empêche une deuxième instance HTTPS pour **ce même dossier projet**.
  Le deuxième lancement affiche une information et sort sans démarrer un autre serveur.
  Cela ne détecte pas une ancienne version du serveur lancée avant cette mise à jour.
- Le port réellement lié, le PID et la date de démarrage du processus sont enregistrés sous
  `.ihm-runtime/https.port` (aucun secret, non versionné). Le serveur de salle lit
  ce registre lors de chaque découverte ; il peut être démarré avant ou après HTTPS.
- La nouvelle annonce UDP v3 transporte ce port ; les clients mis à jour l'utilisent pour
  inscription, activation, demande OTP, confirmation OTP et session HTTPS. L'adresse vient
  toujours du paquet UDP, pas du texte de l'annonce. La découverte reste indicative : TLS
  vérifie l'identité et n'est pas désactivé. Le port du challenge OTP est conservé.
- Les annonces v2 et la découverte historique restent compatibles avec les anciennes versions
  à 8443 ; seules les nouvelles annonces v3 annoncent le port dynamique.
- En secours : Options avancées contient l'adresse et un champ **Port HTTPS**. Entrer le port
  affiché dans la console PC1 si la découverte ne fonctionne pas. Après redémarrage du serveur,
  relancer la découverte et se reconnecter ; les sessions existantes ne migrent pas à chaud.

## Ce qui reste stable

Le serveur de salle et la découverte UDP conservent leur port configuré (7777 par défaut).
Il faut un point de rendez-vous connu pour découvrir les ports des services. PostgreSQL
conserve le port configuré dans Compose/JDBC (5433 par défaut). Ce changement ne prétend pas
rendre tous les ports aléatoires, ni régler les conflits de port Docker/salle, les migrations,
les mots de passe, le DNS ou les droits réseau. Les postes clients n'utilisent pas Docker.

## Déploiement Windows

Après fusion, fermer les interfaces, arrêter l'ancien serveur HTTPS **et le serveur de salle**
avec Ctrl+C, puis exécuter chaque commande séparément :

```powershell
cd "D:\S7\Platforme_intelg_d'apprendre"
git fetch origin
git merge --ff-only origin/main
.\mvnw.cmd clean test
```

Préserver les modifications locales et `postes/configuration-PC1.local.cmd`. Aucune migration
SQL. Ne pas supprimer les volumes Docker. Après BUILD SUCCESS :

```powershell
Start-Process cmd.exe -ArgumentList '/k postes\authentification-PC1.bat'
Start-Process cmd.exe -ArgumentList '/k postes\1-serveur-PC1.bat'
```

Relancer les interfaces mises à jour : Réessayer lance la découverte des ports actuels.
L'ancien lanceur de salle ne sait pas annoncer le port v3 : il faut bien le redémarrer.
Les deux services doivent utiliser le même dossier projet sur PC1. Ne pas supprimer `.ihm-runtime` avec un serveur en fonctionnement : ce dossier contient
son verrou et son registre d'exécution. Il est exclu de Git et indépendant de `mvn clean`.
Arrêter les serveurs avant mise à jour.

Sur PC1, on peut choisir explicitement le mode automatique dans la configuration privée :

```bat
set "IHM_AUTH_PORT=0"
```

Sur un réseau filtré, autoriser le programme Java serveur pour les connexions entrantes
sur le profil réseau approprié, selon la politique de l'établissement. Une règle limitée
uniquement à 8443 ne suffit pas à un port variable. Ne pas désactiver le pare-feu ni TLS.

## Vérifications et limites

Tests Java ajoutés : attribution du port 0, repli sur un port occupé, analyse d'annonce v3,
rejet de ports invalides, compatibilité v2, verrou d'instance et annonce périmée (fixtures
dans un dossier temporaire). Non exécutés dans le sandbox sans JDK/Maven.

Recette Windows/LAN requise : port préféré occupé par un autre programme ; démarrage auto ;
second lancement HTTPS sans doublon ; découverte depuis PC1 puis un autre PC ; inscription,
activation et connexion OTP sur le port annoncé ; arrêt et redécouverte après redémarrage ;
connexion manuelle avec port ; règle pare-feu. L'absence d'erreur de syntaxe et les contrôles
Python ne remplacent pas ces essais.

## Diagnostic d'un port HTTPS inaccessible depuis un autre PC

Un certificat valide et un Wi-Fi de même nom ne prouvent pas que le port TCP est joignable.
Sur PC1, laissez ouvertes **authentification-PC1.bat** et **1-serveur-PC1.bat** et relevez
l'IPv4 de la carte utilisée ainsi que le port réellement affiché par l'authentification.
Le port peut changer après chaque redémarrage, même si une préférence est configurée.

1. Depuis le poste client à jour, exécutez `postes\tester-reseau-https.cmd` et saisissez
   cette IPv4 et ce port. Le test n'envoie ni identifiant ni mot de passe. Un succès indique
   seulement que TCP est ouvert, pas que le certificat ou le compte est valide.
2. En cas d'échec TCP, vérifiez que l'adresse et le port correspondent toujours à PC1, que
   le service écoute encore et qu'aucun VPN, pare-feu ou isolation entre clients Wi-Fi
   (réseau invité / point d'accès) ne bloque les connexions. La découverte UDP peut fonctionner
   alors que TCP est bloqué ; l'inverse est également possible.
3. Si la politique réseau l'autorise, sur **PC1** lancez par clic droit **Exécuter en tant
   qu'administrateur** `postes\autoriser-https-pare-feu.cmd`, après démarrage du service.
   Après confirmation, il crée une règle entrante TCP limitée au programme Java du serveur,
   au port actif, au sous-réseau local et au profil Windows **Privé**. Il ne désactive ni le
   pare-feu ni TLS. Relancez-le après chaque changement de port ou de JDK. Le réseau doit
   effectivement utiliser le profil Privé ; sur un réseau Public ou Domaine, consultez
   l'administrateur réseau plutôt que d'élargir la règle sans autorisation. Pour supprimer
   la règle : Pare-feu Windows avancé → règles de trafic entrant → « IHM 2026 HTTPS
   (port courant) ». Les règles de blocage ou politiques de l'établissement peuvent prévaloir.
4. Si TCP réussit mais que l'application échoue, utilisez `postes\verifier-certificat-https.cmd`
   avec le JDK du client et consultez les journaux HTTPS sur PC1. Le diagnostic de certificat
   utilise un contrôle de nom plus strict que l'application. Ne partagez jamais de mot de
   passe, code OTP, jeton de session, clé privée ou journal contenant ces données.

Si la découverte n'aboutit pas, saisir l'adresse et le port dans les options avancées ; un
port saisi manuellement est conservé pendant les recherches en arrière-plan. Refermez les
options avancées et cliquez sur Réessayer pour revenir à la sélection automatique. La règle
ci-dessus n'ouvre pas UDP 7777 pour la découverte ni le port du serveur de salle : si nécessaire,
faire autoriser ces flux séparément par l'administrateur réseau.
