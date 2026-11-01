# Affectation et correction humaine centralisées

## Portée

Pour les postes connectés avec une vraie session HTTPS :

1. Administrateur : **Comptes et rôles → Affecter un apprenant à un enseignant**.
   Un responsable par apprenant ; les deux comptes doivent être actifs et acceptés.
2. L'enseignant ouvre **Résultats** : ses apprenants sont listés, même sans copie.
   Les copies reçues, sélectionnables, montrent sujet, réponses et code non exécuté.
3. L'apprenant ouvre **Parcours** : niveaux publiés lus dans PostgreSQL et progression
   basée sur les décisions publiées. Il passe/reprend le test disponible, saisit ses
   réponses puis les soumet. Aucun score ni corrigé automatique n'est envoyé au client.
4. Copie **SOUMISE** : l'apprenant voit « en attente », sans note, commentaire ou décision.
5. L'enseignant affecté enregistre un brouillon, puis confirme sa publication :
   note de 0 à 20 (deux décimales max), commentaire obligatoire, décision de validation.
   La validation exige une note au moins égale au seuil photographié au début du test.
6. L'apprenant ouvre **Mes copies et décisions → Actualiser** pour lire la correction.
   La validation met à jour la progression ; le niveau suivant devient accessible.

Les brouillons sont privés au correcteur, la publication est définitive dans cette
version. Un autre enseignant ne voit ni ne corrige cette copie. L'identité de l'élève
et celle du correcteur ne viennent jamais d'un nom fourni par le client, mais de la
session HTTPS et de l'affectation enregistrée sur PC1. Audit transactionnel des opérations.

## Sujets de test existants

Le seed historique ne contient un test que pour Python niveau 3 : il ne suffit pas
pour un nouveau compte au niveau 1. Aucun résultat ou sujet réel n'est inventé ici.
L'administrateur peut utiliser **Configurer le sujet d'un test** pour un niveau publié :
une question à réponse libre par ligne (1 à 20), sans corrigé automatique. Commencer
par le niveau 1 du langage utilisé. Les anciens sujets/réponses ne sont pas supprimés :
les questions précédentes sont désactivées pour les nouvelles copies, et chaque copie
conserve un snapshot de son sujet. Le changement de sujet est interdit si quelqu'un
passe le test. Les QCM déjà présents en base restent lisibles/soumettables.

La création pédagogique de niveaux/cours et certaines autres pages historiques restent
sur le backend de démonstration : cette livraison ne prétend pas migrer toute la gestion
de contenu ni fournir un éditeur complet de barèmes par question. Pas d'exécution de
code reçu, pas de notation par IA. La note et le commentaire portent sur la copie entière.

## Cycle et sécurité

- Tentative démarrée : `EN_COURS`, reprise possible ; réponses non soumises non sauvegardées.
- Abandon explicite : `ANNULEE` (réponses locales perdues). Quitter l'écran seul ne termine
  pas la tentative ; revenir au test pour la reprendre ou l'abandonner.
- Soumission : `SOUMISE`, plus de modification par l'élève ; une seule copie ouverte par test.
- Publication humaine : `CORRIGEE`, note et décision visibles ; progression transactionnelle.
- Nombre maximal de tentatives non annulées, niveau précédent validé, période et publication
  du niveau contrôlés côté serveur. Durée/chronomètre et expiration automatique ne sont
  pas encore implémentés dans ce parcours.
- Réaffectation à un autre enseignant interdite tant qu'une copie reste en cours/à corriger.
  Les copies déjà publiées conservent leur correcteur historique.
- Les appels NVIDIA sont refusés côté serveur si une tentative réelle est `EN_COURS`.
  La fermeture de l'écran ou un client modifié ne supprime donc pas ce verrou.
- Les vues sont actualisées manuellement, pas par notification push ou email automatique.
- Les listes de copies sont limitées aux 200 dernières du compte ; pagination à ajouter
  avant usage à grande échelle. Les affectations n'ont pas encore de groupes multiples.

## Mise à jour Windows (après publication et fusion)

Ne pas réinitialiser Docker ni supprimer de volume, de compte, certificat ou configuration.
Faire d'abord une sauvegarde PostgreSQL avec vos outils habituels et la conserver hors Git.
Fermer les interfaces et arrêter HTTPS avec Ctrl+C, puis à la racine du projet :

```powershell
git fetch origin
git merge --ff-only origin/main
.\mvnw.cmd clean test
```

Exécuter une commande à la fois ; arrêter si une commande échoue. Appliquer la migration
sur la base existante (adapter utilisateur/base si différents de l'installation actuelle) :

```powershell
docker compose -f db/docker-compose.yml cp .\db\10-correction-enseignant.sql postgres:/tmp/10-correction-enseignant.sql
docker compose -f db/docker-compose.yml exec -T postgres psql -U ihm2026 -d ihm2026 -v ON_ERROR_STOP=1 -f /tmp/10-correction-enseignant.sql
```

Attendre COMMIT, redémarrer HTTPS une seule fois et les clients mis à jour. Garder le
serveur de salle existant s'il fonctionne. Pas de nouvelle clé NVIDIA nécessaire.
Le montage Docker initialise aussi la migration sur une base neuve, pas automatiquement
sur un volume existant.

## Recette à trois rôles

1. Admin affecte Samou (APPRENANT) à Sam (ENSEIGNANT).
2. Sam ouvre Résultats : Samou doit apparaître dans le résumé des apprenants affectés.
3. Admin configure le test du niveau 1 ; Samou le passe puis soumet ses vraies réponses.
4. Samou voit SOUMISE sans note. Sam sélectionne la copie, consulte les réponses/code.
5. Sam enregistre un brouillon : Samou ne voit toujours aucune note ni commentaire.
6. Sam publie (confirmation) : Samou actualise et voit la note, le commentaire, la décision.
7. Une validation autorisée débloque le niveau suivant ; un refus ne le débloque pas.
8. Vérifier qu'un autre enseignant/élève n'accède pas aux copies de Samou, que la
   resoumission/republication est refusée et qu'une réaffectation en cours est bloquée.
9. Pendant EN_COURS, tenter un appel IA depuis une autre interface de même compte : refus.
10. Redémarrer HTTPS et vérifier la persistance des affectations et résultats.

Tests PostgreSQL conditionnels : `PostgresPedagogieRepositoryIntegrationTest`, avec
IHM_DB_URL/IHM_DB_USER/IHM_DB_PASSWORD et migrations appliquées. Le test crée des
comptes et un parcours dédiés, puis nettoie ses fixtures ; utiliser une base de test.
Le sandbox n'a pas Java/Maven/PostgreSQL : aucun test Java/JUnit/DB/Windows exécuté.
Les tests statiques et le parsing ne prouvent pas une compilation ni le fonctionnement réel.
