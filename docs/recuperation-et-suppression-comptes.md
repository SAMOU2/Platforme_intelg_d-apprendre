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
