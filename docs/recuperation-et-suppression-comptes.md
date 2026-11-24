# Récupération et suppression des comptes

## Mot de passe oublié

Sur l'écran de connexion de chaque rôle, saisir l'adresse e-mail et choisir **Mot de passe oublié ?**.
Demander un code, puis saisir le code reçu et deux fois le nouveau mot de passe (au moins 12 caractères,
72 octets UTF-8 maximum). Le code de huit chiffres expire après 10 minutes, est utilisable une seule
fois et est bloqué après cinq erreurs. Un bouton permet aussi de saisir un code déjà reçu sans en
redemander. Le compte doit être actif et accepté, y compris pour un administrateur.

Le message de demande reste volontairement identique si le compte n'existe pas ou est inactif. Les
requêtes sont limitées à trois par compte et vingt par adresse source et par heure ; le dernier code
généré remplace le précédent. Le service e-mail de PC1 doit fonctionner : vérifier les courriers
indésirables et les journaux du service si aucun code n'arrive. Après succès, les sessions, codes de
connexion et appareils de confiance de ce compte sont révoqués ; il faut se reconnecter. Le code de
réinitialisation n'est pas un code de connexion ni un PIN d'activation.

### Aucun code ne part : diagnostic

Le serveur attend désormais la fin du traitement DB + SMTP avant d'accepter la demande
(90 secondes maximum, client configuré à 120 secondes). Auparavant, il répondait immédiatement
202 même si l'envoi échouait ensuite en arrière-plan : la boîte de saisie du PIN s'ouvrait sans
qu'aucun message ne soit parti.

Un échec SMTP, une migration manquante ou une file saturée renvoie maintenant une erreur 503
visible dans l'interface ; la saisie du code ne s'ouvre pas automatiquement. La cause SMTP détaillée
reste uniquement dans la console de PC1 (elle peut contenir des données internes du fournisseur).
Le succès signifie **accepté par le serveur SMTP**, pas « arrivé dans la boîte de réception » :
un fournisseur peut encore retarder, filtrer ou rejeter le message ensuite.

Les comptes inconnus, inactifs, non acceptés ou limités par les quotas gardent la même réponse
neutre que les comptes éligibles ; aucun code ni état du compte n'est renvoyé dans le JSON.
Après un délai dépassé, un message peut malgré tout arriver : choisir **J’ai déjà un code** plutôt
que multiplier les demandes (trois par heure maximum).

1. **Lancer `postes\tester-smtp.cmd votre-adresse@example.org` sur PC1.** Le script relit
   `postes\configuration-PC1.local.cmd`, envoie un vrai message et affiche la cause exacte de
   l'échec. Il ne remplace pas l'étape 2 : il confirme d'abord que le SMTP fonctionne.

2. **Lire la ligne `[smtp]` affichée au démarrage** de `postes\authentification-PC1.bat` :

   - « non configuré (IHM_SMTP_HOST ou IHM_SMTP_FROM absent) » : renseigner les deux variables dans
     `postes\configuration-PC1.local.cmd`, puis redémarrer le service. Aucun code ne peut partir
     sans elles.
   - « STARTTLS obligatoire » avec un fournisseur qui impose le port 465 : ajouter
     `set "IHM_SMTP_SSL=1"`, ou passer `IHM_SMTP_PORT` à 465 (le mode SMTPS est alors déduit).
   - « authentification activée » : le couple `IHM_SMTP_USER` / `IHM_SMTP_PASSWORD` doit être
     exact. Un identifiant défini sans mot de passe fait refuser le démarrage.

3. **Relancer une demande** puis relire la console :

   - `envoi du courriel impossible : ... 535 Authentication failed` → identifiant ou mot de passe
     SMTP refusé (beaucoup de fournisseurs exigent un « mot de passe d'application »).
   - `Connection refused`, `timed out`, `UnknownHostException` → hôte, port, pare-feu, réseau ou
     proxy : tester `Test-NetConnection smtp.fournisseur.example -Port 587` dans PowerShell.
   - `Table ou fonction absente ... 15-reinitialisation-mot-de-passe.sql` → la migration 15 n'est
     pas appliquée. Un volume Docker déjà créé ne rejoue pas `db/*.sql`. Redémarrer
     `postes\authentification-PC1.bat` suffit : il applique seul les migrations absentes, après une
     sauvegarde dans `sauvegardes\`. Pour le faire à la main :
     `postes\appliquer-migrations-PC1.cmd -Simulation` puis `postes\appliquer-migrations-PC1.cmd`.
   - `file de réinitialisation pleine` → plus de 100 demandes en attente : réessayer plus tard.

4. **Demande acceptée sans erreur, mais aucun code reçu** : vérifier les indésirables et les journaux
   de livraison du fournisseur. Le compte peut aussi ne pas être éligible (inconnu,
   inactif, inscription non acceptée, ou quota de trois demandes/heure atteint). Vérifier dans
   `Comptes et rôles` que le compte est actif et son inscription acceptée.

Avant usage, sauvegarder la base, appliquer la migration 15 aux bases existantes selon
[README-db](../db/README-db.md#migration-15--mot-de-passe-oublié), redémarrer PC1 et mettre à jour
les clients. Un serveur non migré ne peut pas envoyer le code. Ne pas désactiver TLS ni partager
les codes, mots de passe ou paramètres SMTP. Faire une recette sur une base de test et une boîte
mail de test avant utilisation réelle.

## Suppression par un administrateur

Dans **Comptes et rôles**, cocher un ou plusieurs comptes sur la page affichée, cliquer sur
**Supprimer la sélection**, lire la liste et confirmer. Un lot de 50 comptes maximum est traité
atomiquement : s'il contient un compte disparu ou interdit, aucun n'est supprimé. Seul un
administrateur connecté peut appeler le serveur ; il ne peut pas supprimer son propre compte et
le dernier administrateur actif reste protégé.

**Opération irréversible : effectuer une sauvegarde vérifiée avant toute suppression.** Les
sessions, appareils, codes, invitations et données pédagogiques liées sont supprimés. Supprimer
un enseignant peut aussi supprimer ses parcours, ses niveaux, les copies et progressions d'autres
apprenants ainsi que les affectations concernées ; supprimer un administrateur peut supprimer les
invitations qu'il a créées. Le journal d'audit est conservé, notamment l'adresse et le rôle des
comptes supprimés. Une contrainte non prise en charge annule tout le lot : aucune suppression
partielle n'est annoncée. Si la connexion est interrompue, actualiser la liste pour vérifier le
résultat avant de recommencer. Tester d'abord sur une copie de la base.
