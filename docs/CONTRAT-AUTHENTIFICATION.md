# Contrat d'authentification

Ce contrat est la base de l'implémentation IHM-001. Le rôle local (`--role`) identifie le type de
poste, mais ne constitue jamais une identité ni une autorisation.

## Transport et identité

- Toute opération contenant des credentials ou un jeton utilise HTTPS.
- Le certificat TLS du serveur doit être approuvé par les postes clients et comporter le nom/IP
  utilisé dans l'adresse serveur. Il n'y a pas de repli silencieux vers HTTP.
- La connexion identifie une personne par son e-mail et son mot de passe, puis exige un code à usage
  unique envoyé à cette adresse. Le code expire après 10 minutes, est haché en base, ne peut servir
  qu'une fois et autorise au plus cinq essais.
- Le serveur normalise l'e-mail (espaces extérieurs supprimés, casse ignorée), vérifie le mot de
  passe et retourne le rôle enregistré en base après validation du code. Le serveur ignore tout
  rôle fourni à la connexion.
- Les réponses en cas d'e-mail inconnu, de mauvais mot de passe ou de compte désactivé sont
  identiques côté client.

## Mots de passe et comptes

- Les mots de passe font au moins 12 caractères et au plus 72 octets UTF-8 (limite bcrypt) ; les
  espaces sont permis et ne sont pas supprimés.
- Le hachage est effectué côté serveur avec bcrypt via `pgcrypto`, facteur de travail 12. Seul le
  hachage est persisté ; les mots de passe ne sont jamais journalisés.
- Un compte désactivé ne peut pas ouvrir de session. Sa désactivation invalide immédiatement ses
  sessions.
- Le mécanisme de récupération automatisé par e-mail n'est pas disponible dans cette application.
  La réinitialisation est réservée à l'administration et devra imposer un remplacement du secret
  temporaire à la prochaine connexion.
- Les comptes `demo` insérés par le seed restent exclusivement destinés au développement et ne
  doivent pas être utilisés en production.
- Toute inscription publique crée un compte `EN_ATTENTE`, quel que soit le rôle demandé. Seul un
  administrateur authentifié peut l'accepter ou le refuser. La décision est journalisée et notifiée
  par e-mail; seul un compte accepté peut ouvrir une session.
- Le serveur est découvert automatiquement par diffusion UDP sur le réseau local. Aucun PIN de salle
  partagé n'est utilisé.
- L'administrateur crée un PIN d'inscription unique lié à l'e-mail et au rôle du futur compte.
  Le serveur l'envoie par e-mail à cette adresse; il est valable 48 heures, limité à cinq essais et
  consommable une fois. Seul son hachage bcrypt est stocké en base; le PIN n'est pas renvoyé ni
  affiché dans l'interface administrateur. L'inscription n'envoie donc pas de code à une adresse
  dont le compte n'existe pas encore.
- Si aucun administrateur n'existe en base, le service exige au premier démarrage les variables
  d'environnement `IHM_BOOTSTRAP_ADMIN_NAME`, `IHM_BOOTSTRAP_ADMIN_EMAIL` et
  `IHM_BOOTSTRAP_ADMIN_PASSWORD`. Le compte est créé actif et accepté; retirer ces variables après
  l'amorçage.

## Sessions

- Après authentification, le serveur émet un jeton opaque généré par un générateur cryptographique
  sûr (au moins 256 bits).
- Le jeton est transmis dans `Authorization: Bearer …` uniquement sur HTTPS. Le serveur ne conserve
  que son empreinte et l'identité, le rôle et les dates d'expiration associés.
- Durée maximale : 8 heures ; expiration d'inactivité : 30 minutes. Une activité valide renouvelle
  l'expiration d'inactivité sans dépasser la durée maximale.
- La déconnexion révoque le jeton côté serveur. Les jetons expirés, inconnus, révoqués ou associés
  à un compte désactivé sont refusés.
- Les jetons ne figurent ni dans les journaux, ni dans les paramètres de lancement, ni dans les
  messages d'erreur.

## Limitation et erreurs

- La connexion est limitée à 5 échecs par compte et par adresse source sur une fenêtre de 15 minutes.
- Les erreurs sont génériques pour ne pas révéler l'existence d'un compte.
- Les accès refusés sont enregistrés sans mot de passe, jeton ou autre secret.

## Routes et autorisations

- La sonde de santé peut rester publique et ne révèle que l'état/version du service.
- Inscription avec PIN valide, demande de code et validation du code sont publiques en HTTPS.
  La création des PIN d'inscription est réservée à l'administrateur; déconnexion et toutes les
  routes métier requièrent un jeton actif.
- Le rôle est dérivé de la session côté serveur. L'autorisation est vérifiée sur chaque route,
  indépendamment des contrôles d'interface.
- Les rôles autorisés par route sont documentés et testés ; toute route non explicitement autorisée
  est refusée par défaut.

## Critères de test requis

1. Identifiants valides, rôle retourné depuis la base après code valide, et credentials invalides.
2. E-mail inconnu, mauvais mot de passe, code invalide et compte désactivé donnent un refus générique.
3. Absence, falsification, expiration et révocation du jeton.
4. Appels directs de routes par un rôle non autorisé.
5. Seuil de limitation de tentatives et refus du transport HTTP en clair.
