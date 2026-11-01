# Cours → entraînement avec IA → test → correction → progression

> Complété par la migration 12 : l’enseignant peut maintenant créer ses niveaux et les
> publier pour ses apprenants affectés. Voir [la procédure actuelle](niveaux-enseignant.md).
> Les limites ci-dessous sur l’absence de création de niveaux décrivent la migration 11 seule.

## Expérience apprenant connectée

Chaque carte de niveau expose trois actions numérotées :

1. **Cours et explications** : blocs publiés du niveau choisi, chargés depuis PC1.
2. **Entraînement avec IA** : énoncé publié pour ce niveau et brouillon de code local.
   Le panneau NVIDIA peut recevoir ce code avec la question ; aucun code n'est exécuté.
3. **Test final sans IA** : sujet réel, soumission à l'enseignant, pas de note automatique.

Un bouton est désactivé si le contenu manque ; les libellés indiquent « en préparation ».
Le test exige cours, entraînement et questions disponibles, période ouverte, affectation
active et niveau débloqué. Ces contrôles ne sont pas seulement visuels : le serveur
vérifie l'accès au niveau et les prérequis avant de créer une tentative.

Après soumission, consulter **Mes copies et décisions**. Les brouillons de correction
restent privés ; la publication de l'enseignant affiche note, commentaire et décision.
Une validation autorisée débloque le niveau suivant dans le même parcours. Les copies
et règles de la migration 10 sont conservées, avec blocage de l'IA côté serveur pendant
une tentative EN_COURS. On peut naviguer librement entre cours et entraînement : la
lecture effective et la réalisation de l'exercice ne sont pas tracées comme une condition
obligatoire ; il ne s'agit pas d'un chronométrage ni d'un contrôle d'assiduité.

Les accès Cours/Exercice de la barre latérale demandent une sélection préalable dans
Parcours. Changer de langage efface cette sélection pour éviter un sujet d'un autre langage.
Les anciens cours/exercices fixes du niveau 3 ne sont plus utilisés dans les sessions connectées.

## Préparer un niveau utilisable

Pas de questions ni de cours fictifs générés automatiquement. Pour chaque niveau publié :

- Administrateur : **Publier le cours et l'entraînement d'un niveau**, renseigner cours,
  énoncé et code de départ facultatif, puis confirmer.
- Administrateur : **Configurer le sujet d'un test**, renseigner les questions finales.
- Enseignant : mêmes éditeurs accessibles dans **Mes apprenants et copies**, mais
  uniquement sur les niveaux dont il est l'auteur (`niveaux.cree_par`). Affecter un élève
  ne donne pas le droit de réécrire les contenus partagés des autres enseignants.
- Affecter l'apprenant à son correcteur, puis Actualiser le parcours apprenant.

Les contenus de ces niveaux sont partagés, pas privés à un groupe. Republier archive
les anciennes versions du cours (BROUILLON) et de l'entraînement, sans les supprimer.
Les copies gardent leur sujet photographié. L'éditeur simple remplace le cours actif
par un texte et un entraînement ; ce n'est pas encore un éditeur riche multi-blocs.
Les niveaux sans auteur ou appartenant à un autre enseignant restent gérables par l'admin.

## Migration et déploiement (après publication/fusion)

Sauvegarder la base, fermer les interfaces et arrêter HTTPS. Ne jamais supprimer de volume.

```powershell
cd "D:\S7\Platforme_intelg_d'apprendre"
git fetch origin
git merge --ff-only origin/main
.\mvnw.cmd clean test
```

Exécuter une commande à la fois, continuer seulement si elle réussit. Migrations 01 à 10
requises. Pour la base existante, appliquer ensuite :

```powershell
docker compose -f db/docker-compose.yml cp .\db\11-contenus-niveaux.sql postgres:/tmp/11-contenus-niveaux.sql
docker compose -f db/docker-compose.yml exec -T postgres psql -U ihm2026 -d ihm2026 -v ON_ERROR_STOP=1 -f /tmp/11-contenus-niveaux.sql
```

Attendre COMMIT, relancer HTTPS une seule fois puis les interfaces à jour. Le montage
Docker n'applique cette migration automatiquement que pour une nouvelle base.

## Limites et recette

- L'IA utilise encore son corpus standard embarqué : elle ne fait pas encore de recherche
  dans les nouveaux cours stockés en base. Le brouillon de code peut être transmis à NVIDIA ;
  ne saisir aucune donnée privée. Le cours publié reste affiché indépendamment de l'IA.
- Pas d'exécution de code, de correction automatique, de sauvegarde du brouillon d'entraînement,
  de nouvel outil complet de création de niveaux ou d'affectation de leurs auteurs.
- Les pages hors de ce parcours peuvent encore comporter des données de démonstration.

Tests Windows à effectuer : vérifier les trois boutons sur chaque langage/niveau, les états
« en préparation », les niveaux verrouillés, le changement de langage, le passage cours →
entraînement → test, le refus IA pendant le test, la soumission/correction/publication puis
le déblocage. Vérifier les droits d'édition d'un autre enseignant et la conservation des copies
après republication des cours.

Le test PostgreSQL conditionnel existant couvre aussi publication/lecture des contenus,
propriété enseignant et accès refusé au contenu verrouillé. Nécessite migration 11 en plus
de 10, et une base de test avec langages initialisés. Compilation, JUnit/DB et JavaFX/Windows
non exécutés dans le sandbox sans JDK/Maven/PostgreSQL. Les analyses syntaxiques ne les
remplacent pas. Aucune nouvelle capture d'interface réelle n'est revendiquée.
