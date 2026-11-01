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
