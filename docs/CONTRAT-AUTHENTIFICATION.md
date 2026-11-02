# Contrat d'authentification

Ce contrat est la base de l'implémentation IHM-001. Le rôle local (`--role`) identifie le type de
poste, mais ne constitue jamais une identité ni une autorisation.

## Transport et identité

- Toute opération contenant des credentials ou un jeton utilise HTTPS.
- Le certificat TLS du serveur doit être approuvé par les postes clients et comporter le nom/IP
  utilisé dans l'adresse serveur. Il n'y a pas de repli silencieux vers HTTP.
- La connexion identifie une personne par son e-mail et son mot de passe, puis exige un code à usage
  unique envoyé à cette adresse. Le code expire après 10 minutes, est haché en base, ne peut servir
  qu'une fois et autorise au plus cinq essais. Après la validation de ce code, l'utilisateur peut
  explicitement faire confiance au poste pendant **48 heures au maximum** : le mot de passe reste
  alors vérifié à chaque ouverture, mais aucun nouveau code n'est envoyé avant l'expiration,
  la révocation ou l'oubli du poste.
- Le serveur normalise l'e-mail (espaces extérieurs supprimés, casse ignorée), vérifie le mot de
  passe et retourne le rôle enregistré en base après validation du code ou d'un appareil de
  confiance encore valable. Le serveur ignore tout rôle fourni à la connexion.
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
- Toute inscription publique crée d'abord un compte `EN_ATTENTE` et inactif. Si le bot de tri
  de PC1 est explicitement configuré, il peut pré-accepter uniquement un **apprenant** éligible
  selon le domaine e-mail; le compte reste inactif jusqu'à l'activation par PIN. Les enseignants
  et tous les cas hors politique restent soumis à la décision d'un administrateur authentifié.
  Les décisions sont journalisées et notifiées par e-mail; seul un compte accepté puis activé
  peut ouvrir une session.
- Le serveur est découvert automatiquement par diffusion UDP sur le réseau local. Aucun PIN de salle
  partagé n'est utilisé.
- Après acceptation par l'administrateur ou pré-acceptation d'un apprenant par le bot, le serveur
  crée un PIN d'activation unique lié à l'e-mail et au rôle du compte. Il l'envoie par e-mail à
  cette adresse; il est valable 48 heures, limité à cinq essais et consommable une fois. Seul son
  hachage bcrypt est stocké en base; le PIN en clair n'est pas affiché dans l'interface
  administrateur. L'administrateur peut renvoyer un PIN pour un compte accepté encore inactif.
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

## Appareils de confiance — code e-mail tous les 2 jours

- La confiance est un choix explicite sur l'écran de connexion et n'est créée qu'après un mot de
  passe correct **et** un code e-mail valide. Ne pas cocher cette option sur un poste partagé.
- Le serveur émet un jeton opaque distinct de 256 bits et ne conserve que son empreinte SHA-256
  dans `appareils_confiance`. Le client conserve le jeton dans le profil utilisateur, dans un
  fichier séparé par compte et rôle de poste sous `~/.ihm2026/appareils-confiance/`. Ni le mot de
  passe ni le code e-mail ne sont enregistrés.
- L'expiration est absolue : 48 heures après la validation du code. Les utilisations mettent à
  jour la dernière utilisation, mais ne prolongent jamais cette échéance.
- À chaque nouvelle ouverture, le serveur revérifie le mot de passe, la limitation des échecs,
  l'état actif et le statut `ACCEPTE` du compte. Un jeton de confiance seul ne suffit donc jamais.
- Fermer la fenêtre ou se déconnecter révoque la session courante, sans effacer automatiquement
  la confiance du poste. **« Oublier ce poste de confiance »** supprime le jeton local et demande
  sa révocation au serveur ; le prochain accès exigera un nouveau code. La désactivation du compte
  révoque définitivement tous ses appareils de confiance.
- Si une personne obtient à la fois le fichier local et le mot de passe pendant la fenêtre de
  48 heures, elle n'aura pas besoin du code e-mail : c'est le compromis de confort explicitement
  accepté en cochant l'option. Le profil Windows doit donc rester protégé.

## Limitation et erreurs

- La connexion est limitée à 5 échecs par compte et par adresse source sur une fenêtre de 15 minutes.
- Les erreurs sont génériques pour ne pas révéler l'existence d'un compte.
- Les accès refusés sont enregistrés sans mot de passe, jeton ou autre secret.

## Routes et autorisations

- La sonde de santé peut rester publique et ne révèle que l'état/version du service.
- Inscription publique sans PIN, activation par PIN après acceptation, demande de code et
  validation du code sont publiques en HTTPS. La révocation d'un appareil de confiance exige la
  présentation de son jeton opaque sur HTTPS. Le renvoi d'un PIN et les décisions manuelles ou
  par lot sont réservés à l'administrateur; déconnexion et toutes les routes métier requièrent
  un jeton actif. La liste des comptes est filtrée et paginée côté PostgreSQL.
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
6. Poste de confiance : création seulement après le code, mot de passe toujours exigé, absence
   de code avant 48 heures, nouveau code après expiration/révocation et refus d'un jeton falsifié.

Voir aussi [l'appareil de confiance de 48 heures](appareil-confiance-48h.md) et
[la scalabilité des inscriptions](scalabilite-inscriptions.md) pour les modes du bot,
les filtres administrateur, la migration d'index et les limites de charge restantes.
