# Inscription des apprenants auprès des enseignants

Cette livraison change **qui décide de l'accès aux cours**. L'administrateur ne nomme plus
l'enseignant d'un apprenant : l'apprenant choisit, l'enseignant accepte (ou laisse son bot
accepter), et l'accès s'ouvre.

## Ce que l'administrateur ne fait plus

L'écran **Comptes et rôles** (PC2) ne contient plus :

- « Affecter un apprenant à un enseignant » ;
- « Publier le cours et l'entraînement d'un niveau » ;
- « Configurer le sujet d'un test ».

Ces trois actes sont des actes d'enseignant. Le périmètre administrateur reste la gestion des
comptes : création, approbation des inscriptions (avec l'avis du bot de tri), PIN d'activation,
activation/désactivation, suppression et journal d'audit. Une phrase de périmètre le rappelle en
bas de l'écran.

Côté serveur, ces retraits sont appliqués et pas seulement masqués :

- `affectations` et `affecter` sont refusées pour **tous** les rôles, administrateur compris
  (HTTP 403 avec le motif) ;
- `publier-contenus`, `preparer-test` et `publier-niveau` sont refusées à un administrateur sur le
  niveau d'un enseignant : publier un cours, un entraînement ou un sujet de test est un acte
  d'enseignant. L'administrateur ne garde la main que sur un niveau **sans auteur**
  (`niveaux.cree_par IS NULL`), pour la continuité des contenus créés avant la migration 12.

Un client modifié ne peut donc rétablir ni l'ancienne affectation ni la publication administrative
des cours et des tests.

## Le parcours de l'apprenant

1. **Enseignants et inscription** (nouvelle entrée de la barre latérale apprenant, route
   `offres`) : le catalogue des enseignants actifs du parcours affiché (Java, Python ou C++).
   Un enseignant apparaît dès qu'il a publié une offre ou au moins un niveau. Chaque carte
   montre les informations réellement disponibles : nom, titre de l'offre, présentation, nombre
   et titres des niveaux publiés, présence du bot d'acceptation, état de la demande en cours.
2. **S'inscrire** : une fenêtre demande le **nom** de l'apprenant (pré-rempli avec le nom du
   compte) et un message facultatif (600 caractères maximum). Aucun autre renseignement n'est
   exigé.
3. **La demande est transmise à l'enseignant.** Elle reste `EN_ATTENTE` tant qu'il n'a pas
   décidé. Le catalogue se relit tout seul toutes les 5 secondes : l'acceptation apparaît sans
   action de l'apprenant. Une demande en attente peut être retirée ; une demande refusée ou
   retirée peut être renvoyée.
4. **Le bot peut accepter immédiatement** si l'enseignant a activé l'acceptation automatique sur
   son offre (ou si PC1 est configuré en `IHM_DEMANDE_BOT_MODE=AUTO`). La décision est tracée
   `decide_par = 'BOT'` dans la demande et dans le journal d'audit.
5. **L'accès aux cours est ouvert** dès l'acceptation : la ligne d'accès de
   `affectations_pedagogiques` est écrite, les niveaux publiés de cet enseignant deviennent
   visibles dans **Mon parcours**, et le test final est déverrouillé.

Un apprenant a toujours un seul enseignant responsable. Changer d'enseignant est refusé tant
qu'une copie est `EN_COURS` ou `SOUMISE` avec l'enseignant actuel : on ne perd pas une copie en
attente de correction.

## Cours, exercices et test dans la même fenêtre

Les trois boutons d'un niveau du parcours ouvrent désormais **un seul écran** (route `atelier`) :

```text
1 · Cours et explications → 2 · Entraînement avec IA → 3 · Test final sans IA
```

La barre d'étapes et le bouton « Continuer » font avancer dans cette fenêtre : plus de changement
d'écran entre le cours, l'exercice et le test. Le brouillon d'entraînement reste saisi quand on
passe au test, et les réponses du test ne sont pas perdues si l'on revient lire le cours : le
panneau de test est conservé tant que la copie n'est ni soumise ni abandonnée.

Les règles existantes sont inchangées : une étape non publiée ne s'ouvre pas, l'assistance IA est
retirée de la fenêtre dès l'étape « Test » et reste suspendue tant qu'une copie est ouverte (le
serveur la refuse de toute façon), et la note comme la décision viennent de la correction publiée
par l'enseignant.

## Le côté enseignant

- **Mes niveaux et contenus** (route `conditions`) gagne un volet replié
  « Mon offre de formation (catalogue des apprenants) » : titre, présentation, ouverture aux
  nouvelles demandes et **bot d'acceptation**. Une offre par langage ; elle peut être publiée
  avant les niveaux, mais le catalogue n'affiche que ce qui est réellement publié.
- **Mes apprenants et copies** (route `resultats`, écran d'accueil de l'enseignant) gagne le bloc
  « Demandes d'inscription reçues » : nom saisi, compte apprenant, langage, message, statut,
  décideur (enseignant ou bot), date. **Accepter** ouvre l'accès aux cours publiés ; **Refuser**
  demande un motif transmis à l'apprenant. La liste se relit toutes les 5 secondes.
- Le suivi des copies ne renvoie plus vers l'administrateur pour obtenir des apprenants : il
  indique que les apprenants s'inscrivent eux-mêmes.

## Migration et exploitation sur PC1

Nouvelle migration **`db/16-offres-et-demandes-inscription.sql`** : tables `offres_enseignants`
et `demandes_inscription`, index unique partiel garantissant une seule demande en attente par
couple (apprenant, enseignant), et historique des demandes décidées. Aucune donnée existante
n'est supprimée ; les affectations déjà enregistrées restent valables (les apprenants déjà
affectés voient « Accès accordé » dans le catalogue).

Base neuve : Docker applique la migration automatiquement. Base existante :

```powershell
Get-Content -Raw -Encoding UTF8 .\db\16-offres-et-demandes-inscription.sql | docker exec -i ihm2026-postgres psql -v ON_ERROR_STOP=1 -U ihm2026 -d ihm2026
```

Vérifier que **COMMIT** s'affiche, puis relancer le service HTTPS sur PC1 et les interfaces.
Docker initialise automatiquement les volumes neufs, mais ne migre **pas** les volumes existants :
sur une base déjà en service, cette commande est obligatoire.

Politique du bot, sur PC1 uniquement (variable d'environnement du service HTTPS) :

| `IHM_DEMANDE_BOT_MODE` | Effet |
|---|---|
| absent ou `OFFRES` | Le bot accepte seulement si l'enseignant a activé l'acceptation automatique sur son offre |
| `MANUEL` | Le bot n'accepte jamais : chaque demande attend l'enseignant |
| `AUTO` | Le bot accepte toute demande dont le nom saisi est lisible |

Une valeur inconnue est refusée au démarrage du service, pas devinée. Dans tous les cas, une
offre fermée ou un nom illisible (vide, une seule lettre, aucun caractère alphabétique, plus de
120 caractères) reste à trancher par l'enseignant.

## Recette

1. PC1 : appliquer la migration 16, relancer HTTPS puis les deux services.
2. PC3 (enseignant) : créer un niveau, publier cours et entraînement, préparer le test, publier
   le niveau ; renseigner l'offre (titre, présentation) et laisser le bot **désactivé**.
3. PC4 (apprenant) : **Enseignants et inscription** → l'enseignant apparaît avec ses niveaux
   publiés → **S'inscrire** → saisir son nom → la carte passe à « Demande en attente ».
4. PC3 : **Mes apprenants et copies** → la demande est là → **Accepter la demande**.
5. PC4 : le catalogue passe à « Accès accordé » (sans rafraîchir manuellement), **Mon parcours**
   affiche les niveaux de l'enseignant, et les trois boutons ouvrent cours → exercices → test
   dans la même fenêtre.
6. Variante bot : PC3 active l'acceptation automatique sur son offre ; une nouvelle demande PC4
   est acceptée immédiatement, avec le décideur « Bot de l'offre » visible des deux côtés.
7. PC2 (administrateur) : vérifier que l'écran ne propose plus d'affectation, ni de publication
   de cours, ni de préparation de test.

## Limites assumées

- Un seul enseignant responsable par apprenant ; pas de groupes ni de co-correction.
- Pas de notification push ni d'e-mail à la décision : les deux écrans se relisent toutes les
  5 secondes tant qu'ils sont affichés.
- Le bot d'acceptation est un moteur de règles (comme le bot de tri des inscriptions) : il
  n'appelle aucun modèle, n'évalue aucun travail et n'accorde aucune note.
- Une offre fermée masque l'enseignant du catalogue ; les demandes déjà acceptées ne sont pas
  révoquées par la fermeture.
