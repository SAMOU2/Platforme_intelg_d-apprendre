# Choisir un serveur par son nom

L'écran de connexion recherche les serveurs à l'ouverture (deux secondes maximum),
puis affiche une liste et un bouton **Actualiser**. Choisir le serveur avant de
se connecter, demander un compte ou activer un compte. La découverte n'authentifie
pas un serveur : le certificat HTTPS et les contrôles d'authentification restent obligatoires.
Le serveur de salle doit être démarré ; l'annonce ne garantit pas que le service HTTPS soit prêt.

## Configuration de PC1

Nom par défaut : **Plateforme Samou**. Pour le changer, définir la variable Windows
`IHM_SERVEUR_NOM` dans l'environnement du lanceur du serveur de salle, avant son
lancement (ou utiliser la propriété Java `-Dihm.serveur.nom=...`). Utiliser des noms
distincts si plusieurs serveurs sont présents dans la même salle.

Le protocole réutilise le port UDP du serveur de salle (7777 par défaut).
Autoriser ce port dans le pare-feu Windows uniquement sur le réseau privé nécessaire.
Les ports applicatifs existants et la vérification TLS restent inchangés.
La découverte interroge localhost, la diffusion globale et les adresses de diffusion
des interfaces actives. Elle ne traverse pas les routeurs ni les Wi-Fi isolant les clients.
Les anciennes requêtes de découverte restent prises en charge.

## Secours

Déplier **Connexion avancée — adresse manuelle** pour renseigner l'adresse IP ou
le nom du serveur. Ne pas ajouter de protocole ni de port. Une adresse déjà fournie
par `ihm.serveur` est conservée. Pas de mémorisation persistante ajoutée à ce stade.
Si le certificat n'inclut pas l'adresse utilisée, corriger le certificat et sa chaîne
de confiance ; ne jamais désactiver la vérification HTTPS.

## Vérifications à faire sur Windows

1. ` .\mvnw.cmd clean test` : vérifier BUILD SUCCESS.
2. Redémarrer le serveur de salle et les interfaces mises à jour ; conserver HTTPS ouvert.
3. Sur le même PC : vérifier l'apparition du nom, sélectionner et se connecter avec OTP.
4. Sur un autre PC du LAN : même test, sans saisie d'IP.
5. Deux serveurs aux noms distincts : vérifier les deux choix et leur destination.
6. Arrêter la salle, Actualiser : aucun résultat ; tester la connexion manuelle.
7. Vérifier demande de compte et activation après sélection.

Les tests JUnit couvrent les annonces invalides et les réponses UDP anciennes/nouvelles.
Ils ne remplacent pas les tests JavaFX, Windows et multi-PC. Aucun de ces tests Java
n'a été exécuté dans le sandbox, qui ne dispose pas de JDK/Maven.

## Choix du Wi-Fi sous Windows

Le formulaire affiche maintenant une étape Wi-Fi avant la liste des serveurs.
`netsh wlan show networks mode=ssid` liste les SSID ; `netsh wlan show interfaces`
indique les réseaux connectés. Aucun profil enregistré et aucun mot de passe ne sont lus.
Les appels passent par PowerShell, hors du thread JavaFX, avec délai maximal de 12 s.
Aucun SSID n'est incorporé dans une commande shell.

1. Choisir le Wi-Fi du serveur, puis **Utiliser ce Wi-Fi**.
2. Si Windows confirme que ce SSID est connecté, la recherche du serveur démarre.
3. Sinon, les paramètres Wi-Fi Windows s'ouvrent. Windows gère la connexion et le secret.
4. La plateforme vérifie la connexion toutes les quatre secondes pendant environ deux minutes.
5. Après connexion, choisir le serveur puis se connecter au compte (OTP inchangé).

Le scan Windows peut dépendre des autorisations de localisation, du service WLAN,
du pilote et de la présence d'une carte Wi-Fi. Une erreur ou l'absence de Wi-Fi ne
bloque pas la recherche classique sur le réseau actuel ni la connexion avancée.
Sur les autres OS, le choix Wi-Fi est désactivé ; la découverte LAN reste disponible.
Le bouton Actualiser de la liste des serveurs permet aussi un usage Ethernet.
La découverte LAN existante interroge toutes les interfaces actives, pas exclusivement
la carte Wi-Fi choisie : avec plusieurs réseaux simultanés (Ethernet/VPN), elle peut
également afficher des serveurs accessibles par ces interfaces. Un SSID ne prouve
pas l'identité d'un serveur, qui reste vérifiée via HTTPS.

### Recette complémentaire Windows (à exécuter)

- Wi-Fi déjà connecté : sélectionner son SSID, vérifier la recherche du serveur.
- Autre Wi-Fi : vérifier l'ouverture de Windows, se connecter via Windows et attendre
  la découverte ; aucun champ de mot de passe Wi-Fi ne doit apparaître dans JavaFX.
- Annuler la connexion : aucun serveur ne doit être sélectionné automatiquement ;
  attendre la fin de surveillance ou sélectionner à nouveau un réseau.
- Localisation refusée, WLAN arrêté, aucune carte Wi-Fi : message explicite et
  recherche LAN/connexion manuelle toujours utilisables.
- Tester SSID accentués et Windows français/anglais : leur encodage dépend de netsh
  et du système ; les tests unitaires de parsing ne prouvent pas cette compatibilité réelle.

Tests JUnit de parsing ajoutés, mais non exécutés dans le sandbox sans Java/Maven.
