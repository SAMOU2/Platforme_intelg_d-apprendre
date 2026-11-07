# Code de connexion seulement tous les 2 jours

## Ce que voit l'utilisateur

Sur un poste personnel, l'utilisateur peut cocher **« Faire confiance à ce poste pendant 2 jours »**
avant de se connecter.

1. La première connexion reste complète : e-mail, mot de passe, puis code à six chiffres reçu par
   e-mail.
2. Une fois le code validé, le serveur reconnaît ce poste pendant 48 heures.
3. Si l'application est fermée puis rouverte pendant ces 48 heures, l'utilisateur saisit encore
   son e-mail et son mot de passe, mais aucun nouveau code n'est envoyé.
4. Après 48 heures, après un clic sur **« Oublier ce poste de confiance »**, ou si le compte est
   désactivé/non accepté, un nouveau code devient obligatoire.

La case ne doit pas être cochée sur un ordinateur partagé par plusieurs personnes.

## Pourquoi le mot de passe reste demandé ?

Le jeton de confiance remplace seulement la preuve récente de possession de la boîte e-mail. Il ne
remplace pas le mot de passe :

- le mot de passe est revérifié à chaque ouverture ;
- les cinq échecs en 15 minutes restent limités comme avant ;
- le compte doit toujours être actif et `ACCEPTE` ;
- le rôle reste lu depuis PostgreSQL, jamais depuis le client.

## Fonctionnement technique

Après validation du code, PC1 génère un jeton opaque de 256 bits, distinct du jeton de session.
PostgreSQL ne stocke que son empreinte SHA-256 dans `appareils_confiance`, avec une expiration
**absolue** à 48 heures. Utiliser le poste ne prolonge pas cette date ; le code sera donc bien
redemandé au plus tard après deux jours.

Le client stocke le jeton dans le profil de l'utilisateur, sous
`~/.ihm2026/appareils-confiance/`, avec un fichier distinct par compte et rôle de poste. Il ne
stocke ni mot de passe, ni code e-mail, ni PIN d'activation. Fermer l'application révoque la
session active mais conserve ce jeton de confiance ; c'est ce qui permet de rouvrir l'application
sans nouveau code pendant la fenêtre autorisée.

Toutes les transmissions continuent à utiliser le service HTTPS existant. Le changement de réseau
Wi-Fi ou d'adresse IP du serveur ne fait pas partie de l'empreinte du poste : la découverte et la
validation du certificat restent celles déjà utilisées par la plateforme.

## Oublier ou révoquer un poste

Sur l'écran de connexion, saisir l'e-mail du compte puis cliquer sur
**« Oublier ce poste de confiance »**. Le client tente de supprimer le jeton local et demande à
PC1 de révoquer ce jeton précis. Les autres postes, les autres comptes et les sessions ouvertes
ne sont pas supprimés. Si PC1 est inaccessible, la suppression locale peut quand même réussir,
mais la révocation serveur n'est pas confirmée : le jeton serveur expirera au maximum 48 heures
après sa création. Le client ne conserve pas le jeton supprimé pour retenter la révocation.

Si la suppression locale échoue (fichier verrouillé ou droits insuffisants), l'interface le
signale et tente quand même de révoquer le jeton sur PC1 s'il a pu être lu. Si les deux opérations
échouent, il faut corriger l'accès au fichier et la connexion, puis réessayer. Si le jeton local
est illisible ou expiré, il peut être supprimé, mais la révocation serveur ne peut pas être
confirmée sans ce jeton. En cas d'échec de base de données ou de migration manquante, le serveur
ne prétend pas avoir révoqué le poste.

Désactiver le compte empêche immédiatement toute nouvelle session et révoque définitivement ses
postes de confiance : après une éventuelle réactivation, un nouveau code e-mail sera nécessaire.

## Migration PC1

La migration est non destructive : `db/14-appareils-confiance-48h.sql` crée uniquement la table
`appareils_confiance` et ses index. Sur une base existante, sauvegarder d'abord, arrêter le
service HTTPS, puis appliquer la migration avec `ON_ERROR_STOP=1` avant de redémarrer PC1.

## Recette de test

1. Se connecter une première fois **sans** cocher la case : fermer puis rouvrir l'application ;
   un nouveau code doit être demandé.
2. Se reconnecter en cochant la case et en validant le code ; fermer puis rouvrir l'application ;
   saisir le mot de passe : la session doit s'ouvrir sans nouvel e-mail.
3. Sur l'écran de connexion, cliquer sur **« Oublier ce poste de confiance »** ; la connexion
   suivante doit redemander un code.
4. En test d'intégration, reculer `expire_le` dans `appareils_confiance` : le serveur doit
   redemander un code.
5. Vérifier qu'un mauvais mot de passe reste refusé même avec un jeton valide, et qu'un compte
   désactivé ne peut pas utiliser la confiance.
