# Scalabilité des inscriptions et de l'administration des comptes

## Remarque traitée

Le flux historique demandait à l'administrateur d'accepter ou de refuser **chaque**
inscription, une par une, après chargement de tous les comptes dans l'écran
d'administration. Avec 100 000 demandes, ce modèle crée trois goulots :

1. l'administrateur devient un point de blocage humain ;
2. l'interface charge et affiche une liste potentiellement énorme ;
3. chaque décision est traitée isolément, sans file filtrable ni lot.

La réponse retenue combine les deux pistes proposées — **bot de tri** et
**filtrage par l'administrateur** — sans confier les rôles sensibles ou la pédagogie à
une IA générative.

## 1. Bot IA de tri : règles explicables, pas de décision opaque

Le bot est un moteur de règles côté serveur (`BotInscription`). Il ne transmet ni nom,
ni e-mail, ni mot de passe à NVIDIA et n'appelle aucun modèle de langage. Il analyse
seulement le rôle demandé et le domaine e-mail, selon la politique explicitement
configurée sur PC1.

| Mode PC1 | Effet |
|---|---|
| `MANUEL` (défaut) | Aucune pré-acceptation : comportement historique. |
| `DOMAINES` | Pré-accepte uniquement les **apprenants** dont le domaine, ou un sous-domaine, figure dans `IHM_INSCRIPTION_DOMAINES_AUTO`. |
| `APPRENANTS` | Pré-accepte les apprenants non bloqués par `IHM_INSCRIPTION_DOMAINES_BLOQUES`. À réserver aux déploiements qui acceptent ce niveau d'ouverture. |

Au démarrage, PC1 affiche `[inscription-bot] Mode du bot de tri des apprenants : ...`
afin de vérifier la politique réellement chargée, sans afficher de secret.

Configuration type dans le fichier **local et privé** de PC1 :

```bat
set "IHM_INSCRIPTION_BOT_MODE=DOMAINES"
set "IHM_INSCRIPTION_DOMAINES_AUTO=univ.dz"
REM Facultatif :
set "IHM_INSCRIPTION_DOMAINES_BLOQUES=jetable.example"
```

Garde-fous :

- une inscription publique ne peut toujours demander que `APPRENANT` ou `ENSEIGNANT` ;
- le bot ne pré-accepte **jamais** un enseignant : la condition est aussi vérifiée dans
  la requête SQL de transition ;
- un compte pré-accepté reste **inactif** jusqu'à la saisie du PIN à 8 chiffres envoyé
  à l'adresse e-mail (48 h, usage unique, cinq essais maximum) ;
- la connexion exige ensuite le mot de passe et le code e-mail à usage unique déjà
  existants ;
- l'action est journalisée sous `ACCEPTATION_BOT_INSCRIPTION`, distincte d'une
  acceptation par l'administrateur ;
- si l'e-mail du PIN échoue, l'administrateur peut renvoyer un nouveau PIN depuis
  l'écran des comptes; l'ancien est invalidé.

Ainsi, dans un déploiement scolaire, la majorité des apprenants d'un domaine
institutionnel n'attend plus une action manuelle individuelle. L'administrateur traite
les enseignants et les exceptions.

## 2. Filtrage administrateur exécuté par PostgreSQL

`GET /api/admin/comptes` ne renvoie plus toute la table. L'écran **Comptes et rôles**
envoie les paramètres suivants au serveur, qui construit une requête paramétrée :

- recherche par nom ou e-mail ;
- domaine e-mail exact, par exemple `univ.dz` ;
- statut d'inscription : en attente, accepté, refusé ;
- rôle ;
- comptes actifs ou inactifs ;
- page et taille de page (50 lignes dans l'interface).

La réponse contient uniquement la page demandée, le total filtré et les compteurs par
statut. Les demandes en attente sont présentées par ordre d'arrivée afin d'éviter
qu'une ancienne demande reste indéfiniment derrière les nouvelles.

Chaque ligne affiche aussi :

- la date de création ;
- l'avis du bot (`acceptation conseillée`, `révision admin`, `refus conseillé` ou
  `déjà traité`) et son motif en infobulle.

L'administrateur peut cocher plusieurs demandes de la page, puis les accepter ou les
refuser après confirmation. Le client découpe l'envoi en lots de 20 pour borner chaque
requête HTTPS; le rapport final distingue les demandes traitées, les erreurs et les
e-mails non envoyés. Une décision enregistrée n'est pas annulée par un échec SMTP :
le PIN peut être renvoyé.

## 3. Index PostgreSQL

La migration non destructive `db/13-scalabilite-comptes.sql` ajoute trois index :

- file par statut et date d'arrivée ;
- filtre rôle/statut/date ;
- domaine e-mail/statut/date.

Sur une base existante : sauvegarder d'abord, arrêter le service HTTPS, puis appliquer
les migrations manquantes dans l'ordre avec `ON_ERROR_STOP=1`. Ne pas supprimer le
volume Docker et ne pas réinitialiser la base pour appliquer cette migration.

## 4. Recette manuelle Windows

1. Appliquer `db/13-scalabilite-comptes.sql` sur la base sauvegardée de PC1.
2. Configurer temporairement le bot en mode `DOMAINES` avec un domaine de test que
   vous contrôlez, puis relancer PC1.
3. Créer une demande **apprenant** avec ce domaine : le message doit annoncer la
   pré-acceptation et l'e-mail doit contenir le PIN. Le compte ne doit pas pouvoir se
   connecter avant l'activation par PIN.
4. Créer une demande **enseignant** avec le même domaine : elle doit rester en attente.
5. Créer une demande apprenant hors domaine : elle doit rester en attente.
6. Dans **Comptes et rôles**, filtrer par statut, rôle et domaine; vérifier la
   pagination, les compteurs et l'avis du bot.
7. Cocher deux demandes en attente et tester une acceptation par lot, puis vérifier
   les deux e-mails et le rapport affiché.
8. Tester un refus par lot sur des comptes jetables uniquement.
9. Revenir au mode souhaité pour l'exploitation réelle. Le mode `APPRENANTS` ne doit
   pas être activé par inadvertance.

## 5. Limites restantes avant un vrai passage à 100 000 comptes

Cette évolution supprime le blocage principal du parcours, mais ne constitue pas à elle
seule une preuve de charge :

- SMTP reste synchrone lors d'une pré-acceptation ou d'un lot ; un très grand volume
  demanderait une file d'e-mails transactionnelle avec reprises et supervision ;
- il faut mesurer PostgreSQL et le service Java avec des données volumineuses ;
- une protection anti-abus complémentaire (limitation par IP/e-mail, surveillance des
  domaines jetables, éventuel SSO institutionnel) reste à décider selon le contexte ;
- plusieurs administrateurs/délégués peuvent devenir nécessaires organisationnellement,
  mais leurs périmètres doivent rester journalisés côté serveur ;
- le bot ne doit pas évoluer vers une décision opaque fondée sur le nom ou un profil :
  toute future règle doit rester explicable, testable et contestable par un humain.

Aucun changement n'est apporté à la correction des tests : l'IA reste bloquée pendant
les tests finaux et la note, la validation et le déblocage restent exclusivement
humains.
