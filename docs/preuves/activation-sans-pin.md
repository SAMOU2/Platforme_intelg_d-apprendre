# Demande sans PIN, décision puis activation

## Parcours corrigé

1. L'apprenant ou l'enseignant envoie nom, e-mail et mot de passe depuis son poste.
   Aucun PIN n'est demandé. L'API refuse le rôle administrateur sur l'inscription publique.
2. Le compte est `EN_ATTENTE` et `actif=false`. Il ne peut pas se connecter.
3. L'administrateur accepte ou refuse. Un refus reste inactif et ne crée aucun PIN.
4. Une acceptation passe à `ACCEPTE` **sans activer le compte** et crée un PIN aléatoire à
   huit chiffres, haché bcrypt, dans la même transaction PostgreSQL.
5. Le serveur envoie la décision et le PIN par SMTP après commit. Le PIN n'est pas retourné
   dans le JSON administrateur, ni affiché dans son interface.
6. Le demandeur clique « Activer mon compte avec le PIN reçu », saisit e-mail et PIN.
   Le serveur vérifie e-mail, rôle, décision, expiration et tentatives ; consommation du PIN,
   activation et audit sont atomiques. Un PIN ne peut pas activer deux fois le compte.
7. La connexion habituelle reste e-mail/mot de passe puis code de sécurité à six chiffres.

## Expiration et récupération

Le PIN dure 48 heures et accepte cinq essais. Après expiration, blocage ou échec d'envoi,
l'administrateur utilise « Renvoyer le PIN d'activation » pour un compte déjà accepté et
inactif, en indiquant son e-mail et son rôle. Un nouveau PIN invalide les anciens.
Un échec SMTP ne revient pas sur une décision déjà enregistrée : actualiser la liste,
puis renvoyer le PIN après correction SMTP. Le bouton générique « Activer » ne permet pas
de contourner le PIN d'une demande acceptée dont le PIN n'a pas été consommé.

## Déploiement

- Mettre à jour le serveur HTTPS **et** les clients ; leurs contrats d'inscription ont changé.
- Redémarrer le service HTTPS et rouvrir les fenêtres apprenant/enseignant/admin.
- Base : migrations 07, 08 et **09** requises. Pas de migration supplémentaire : la table
  historique `invitations_inscription` conserve maintenant les PIN d'activation après décision.
- Ne pas supprimer de volume, ni relancer le seed. Les comptes actifs existants sont conservés.
- Les anciennes invitations préalables ne sont plus nécessaires. Pour une demande déjà en
  attente, l'accepter produit son nouveau PIN ; pour un compte accepté inactif, utiliser le renvoi.
- Le lanceur HTTPS utilise désormais `for /f` au lieu de `set /p` pour éviter la troncature du
  classpath qui empêchait le chargement du pilote PostgreSQL sous Windows.

## Vérifications et limites

Exécutés ici : `git diff --check`, **16 contrôles Python de déploiement/contrats UI**, analyse
syntaxique Tree-sitter des fichiers Java. Ces contrôles ne sont ni une compilation ni des tests
fonctionnels de la base. Journal : `activation-controles.txt`.

Ajoutés/adaptés dans `PostgresCompteRepositoryIntegrationTest` : parcours complet pour les deux
rôles, compte bloqué avant activation, mauvais rôle, PIN à usage unique, cinq mauvais essais,
expiration, renvoi, refus et interdiction du rôle administrateur public. Ils exigent JDK 25,
une base de test dédiée migrée et un administrateur actif. **Non exécutés dans ce sandbox**
(JDK/PostgreSQL absents). Sans `IHM_DB_URL`, les tests d'intégration sont ignorés.

### À valider sur les PC

1. `mvnw.cmd clean test` avec `IHM_DB_URL`, `IHM_DB_USER`, `IHM_DB_PASSWORD` pointant sur une
   base de test, jamais la base de production.
2. Créer un apprenant sans PIN ; vérifier son absence d'accès avant la décision.
3. Accepter, vérifier réception de l'e-mail et maintien du compte inactif.
4. Activer avec le PIN, puis se connecter avec le code distinct à six chiffres.
5. Refaire le parcours enseignant, puis tester refus, erreur PIN, renvoi et échec SMTP.
6. Vérifier qu'un compte administrateur déjà actif peut toujours se connecter et annuler la
   saisie du code de connexion. Aucun nouveau PNG de fenêtre JavaFX n'a été produit ici.

Limites : pas de file d'envoi SMTP durable ni de notification temps réel (actualiser les comptes).
L'inscription publique exige une supervision/régulation supplémentaire avant exposition Internet.
