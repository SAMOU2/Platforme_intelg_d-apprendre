# Préparer et publier les niveaux : rôle enseignant

Cette fonctionnalité complète la migration 11. L'administrateur n'a plus à créer les
niveaux ni à remplir les contenus pour que l'enseignant puisse commencer.

## Écran enseignant connecté

Ouvrir **Mes niveaux et contenus** (l'ancien écran « Créer un niveau (démo) » n'est plus
utilisé pour une session connectée).

1. **Créer un niveau** : choisir Python/Java/C++, le titre, le seuil /20 et le nombre de
   tentatives (1–10). Le serveur crée le parcours de cet enseignant pour ce langage si
   nécessaire, attribue le prochain rang et enregistre l'auteur depuis la session.
   Le niveau reste **BROUILLON**, invisible aux apprenants.
2. **Publier cours et entraînement** : sélectionner son niveau, saisir cours, énoncé et
   éventuellement code de départ. Les contenus sont disponibles pour préparation, mais
   le niveau brouillon reste invisible aux apprenants.
3. **Préparer le test final** : sélectionner son niveau, saisir les questions finales.
   Contrairement à l'ancienne version, la préparation fonctionne aussi sur un brouillon.
4. Sélectionner le niveau dans la liste, puis **Publier le niveau sélectionné** et confirmer.
   Le serveur exige cours, entraînement, questions actives et niveau précédent publié.

Le catalogue montre les brouillons et niveaux publiés de l'auteur. Les droits sont aussi
vérifiés sur le serveur : changer un identifiant dans une requête ne permet pas de préparer
ni de publier le niveau d'un autre enseignant. L'administrateur conserve ses accès de gestion
existants ; ils ne sont pas une étape obligatoire de cette procédure.

## Apprenants affectés et parcours historiques

- L'administrateur continue d'approuver les comptes et d'affecter les apprenants aux enseignants.
- Un enseignant possède un parcours distinct par langage. Ses niveaux publiés sont visibles
  par ses apprenants affectés ; les niveaux privés des autres enseignants ne le sont jamais.
- Créer un brouillon ne masque pas le parcours historique. **La première publication dans
  un langage active le parcours de l'enseignant pour ses apprenants affectés dans ce langage.**
  Une confirmation dans l'interface explique cette bascule.
- Les copies et notes historiques sont conservées et consultables dans le suivi. Elles ne
  valident pas automatiquement les nouveaux niveaux : le parcours de l'enseignant commence
  au niveau 1. Les autres langages conservent leurs parcours existants.
- Si un apprenant affecté a encore une copie EN_COURS ou SOUMISE dans un ancien parcours du
  même langage, le serveur refuse la publication jusqu'à abandon ou correction/publication.
  Les débuts de test et publications utilisent le verrou apprenant pour éviter de masquer
  une tentative lors d'une publication concurrente.
- Sans niveau publié dans le parcours de l'enseignant pour ce langage, les niveaux historiques
  restent affichés. Ils ne sont pas attribués arbitrairement à l'enseignant et leurs auteurs
  ne sont pas modifiés. Les anciens niveaux partagés restent partagés ; l'isolation par
  affectation concerne les nouveaux parcours enregistrés dans `parcours_enseignants`.

Le parcours apprenant reste : **Cours → Entraînement avec IA → Test sans IA → Soumission →
Correction de l'enseignant → Note et décision publiées → Niveau suivant si validé**.
Actualiser le parcours apprenant après publication. Aucun cours ni sujet n'est inventé ou
publié automatiquement ; l'enseignant choisit les contenus pédagogiques.

## Déploiement Windows après fusion

Ne pas effacer les volumes Docker ni remplacer `postes/configuration-PC1.local.cmd`.
Fermer les interfaces et arrêter HTTPS, sauvegarder la base puis exécuter séparément :

```powershell
cd "D:\S7\Platforme_intelg_d'apprendre"
git fetch origin
git merge --ff-only origin/main
.\mvnw.cmd clean test
```

Continuer seulement après réussite de chaque commande. Si Git signale des modifications
locales, les préserver ; ne pas faire de reset forcé. Après les migrations 10 et 11 :

```powershell
docker compose -f db/docker-compose.yml cp .\db\12-parcours-enseignants.sql postgres:/tmp/12-parcours-enseignants.sql
docker compose -f db/docker-compose.yml exec -T postgres psql -U ihm2026 -d ihm2026 -v ON_ERROR_STOP=1 -f /tmp/12-parcours-enseignants.sql
```

Attendre **COMMIT**, relancer HTTPS une seule fois et les interfaces à jour. Pour une base
neuve, Docker exécute aussi la migration 12 automatiquement. Sur une base existante, le
montage seul ne suffit pas.

## Recette et limites

Tester avec deux enseignants et leurs apprenants affectés :

- création niveau 1 et niveau 2, visibilité des brouillons dans le catalogue de l'auteur ;
- refus publication incomplète et refus publication du niveau 2 avant le niveau 1 ;
- refus lecture/édition/publication par l'autre enseignant ou par un apprenant ;
- ancienne copie ouverte : refus bascule ; après publication de sa correction : bascule ;
- apprenants : seuls niveaux publiés de leur enseignant, niveau 2 verrouillé avant validation ;
- cours, entraînement IA, test sans IA, soumission, correction humaine, publication et déblocage ;
- conservation des notes et copies historiques après bascule.

Le test PostgreSQL conditionnel de `PostgresPedagogieRepositoryIntegrationTest` est étendu
pour ces règles. Nécessite une base de test initialisée jusqu'à 12. Non exécuté dans le sandbox
sans JDK/Maven/PostgreSQL : tests Python et analyses syntaxiques ne prouvent pas la compilation
ni le fonctionnement JavaFX/Windows. Aucune nouvelle capture réelle n'est revendiquée.

Les éditeurs de cours/test restent des formulaires simples de remplacement avec choix du
niveau. Pas encore de rechargement/modification riche des blocs existants, de suppression de
niveau, de changement de rang, de transfert d'auteur, de groupes multiples par enseignant
ou de calendrier de publication dans le nouvel écran. Les limites IA/exécution/sauvegarde
d'entraînement décrites dans `parcours-par-niveau.md` restent applicables. Un changement
ultérieur d'enseignant affiche le parcours du nouvel enseignant sans transférer les validations
entre parcours ; les anciennes copies restent dans le suivi de leurs correcteurs historiques.

## Actualisation automatique côté apprenant

Les vues connectées Parcours, Cours et Exercice interrogent PC1 toutes les cinq secondes
pendant qu'elles sont affichées. L'ouverture de la vue déclenche aussi une lecture immédiate.
Le bouton manuel du parcours reste disponible mais n'est plus nécessaire pour détecter
une publication, une affectation ou une validation publiée. Le mécanisme est du polling,
pas une notification poussée : la détection dépend du réseau et du délai de réponse de PC1.

Les réponses identiques ne reconstruisent pas la page. Une seule lecture par vue peut être
en cours ; les réponses reçues après navigation/fermeture sont ignorées, et le minuteur est
arrêté quand la vue quitte la scène ou que sa fenêtre est fermée. Les erreurs de connexion
apparaissent dans la vue et sont retentées automatiquement, sans notification répétée.

Si le niveau ouvert n'appartient plus au parcours actif (par exemple publication du parcours
enseignant remplaçant les niveaux historiques), l'apprenant retourne automatiquement au
parcours à jour. Un brouillon d'entraînement modifié empêche cette navigation automatique :
il reste affiché avec un avertissement invitant à le copier avant de revenir au parcours ;
le test est désactivé et le panneau IA retiré pour ce niveau obsolète. Le code saisi n'est
pas écrasé par la republication d'un entraînement. Il reste un brouillon local non sauvegardé.

Ce polling n'est pas installé dans le test final : il ne recharge pas les questions d'une
copie en cours et n'efface pas les réponses. Il ne couvre pas encore toutes les autres vues
(notamment la liste des copies). Il ne publie aucun contenu, ne corrige aucune affectation et
ne débloque pas un niveau non validé.

Recette Windows : laisser Parcours/Exercice ouvert pendant qu'un enseignant publie ; vérifier
l'apparition automatique sous quelques secondes, la bascule de l'ancien niveau sans brouillon,
la conservation d'un code modifié, l'absence de doublons de requêtes lors d'un réseau lent,
l'arrêt après fermeture/navigation et la reprise après reconnexion. Essais JavaFX à effectuer ;
les contrôles Python sont structurels et ne remplacent pas cette recette.
