# Choisir un serveur par son nom

L'écran de connexion recherche les serveurs à l'ouverture (deux secondes maximum),
puis sélectionne automatiquement l’unique serveur, ou affiche une liste s’il y en a
plusieurs. Un bouton **Réessayer** relance la recherche. La découverte n'authentifie
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

Déplier **Options avancées — adresse manuelle** pour renseigner l'adresse IP ou
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
6. Arrêter la salle, Réessayer : aucun résultat ; tester la connexion manuelle.
7. Vérifier demande de compte et activation après sélection.

Les tests JUnit couvrent les annonces invalides et les réponses UDP anciennes/nouvelles.
Ils ne remplacent pas les tests JavaFX, Windows et multi-PC. Aucun de ces tests Java
n'a été exécuté dans le sandbox, qui ne dispose pas de JDK/Maven.

## Parcours simplifié : Windows gère le réseau

La liste de Wi-Fi et sa surveillance ont été retirées du formulaire commun aux trois rôles.
L'application ne scanne plus les SSID et ne nécessite donc plus l'autorisation de localisation
pour sa connexion. Les fonctions de lecture Windows restent dans le service utilitaire,
mais ne sont plus appelées par l'interface de connexion.

- Un serveur détecté : sélection automatique, liste cachée ; connexion au compte possible.
- Plusieurs serveurs : liste visible, choix explicite requis (pas de choix arbitraire).
- Aucun serveur : message invitant à vérifier le réseau et PC1, puis **Réessayer**.
- **Ouvrir les paramètres Wi-Fi Windows** ouvre les paramètres Windows sans fournir
  ni demander de secret. Après changement réseau, cliquer sur **Réessayer**.
- **Options avancées** conserve une adresse manuelle, y compris celle configurée
  par `ihm.serveur`. Une adresse manuelle saisie dans le panneau ouvert n'est pas
  remplacée par la découverte. Une ancienne sélection automatique est effacée au
  début d'une nouvelle recherche.

La découverte interroge les interfaces LAN actives, y compris Ethernet ; elle ne
prouve ni l'identité du serveur ni la disponibilité de l'authentification HTTPS.
La vérification des certificats reste obligatoire ; les comptes, OTP et PIN sont inchangés.
Un changement d'IP nécessite un certificat valide pour la nouvelle adresse ou le nom utilisé.

### Recette de l'interface simplifiée (Windows, à exécuter)

1. Un serveur de salle démarré : ouvrir chacun des trois rôles ; aucune liste Wi-Fi,
   aucun choix de serveur obligatoire, compte accessible après sélection automatique.
2. Vérifier connexion avec OTP, inscription et activation sans saisie IP.
3. Aucun serveur : Réessayer doit afficher un diagnostic et ne pas conserver une
   ancienne adresse automatique. Tester le secours manuel.
4. Plusieurs serveurs : vérifier que la liste apparaît et qu'aucun n'est choisi au hasard.
5. Ouvrir les paramètres Windows, changer de réseau, revenir et Réessayer.
6. Localisation Windows désactivée : découverte et connexion toujours possibles.
7. Hors Windows ou sur Ethernet : pas de bouton Windows ; découverte LAN disponible.
8. Vérifier qu'aucun changement de serveur/réseau n'est permis pendant l'authentification.

Les contrôles Python sont statiques ; ils ne remplacent pas compilation, JUnit,
JavaFX et essais Windows/multi-PC, non exécutés dans le sandbox sans JDK/Maven.
