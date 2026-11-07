# Installateurs Windows des postes clients

Le générateur produit **trois MSI x64** : apprenant (PC4), enseignant (PC3) et administrateur
(PC2). Chaque application embarque son propre runtime Java et JavaFX et démarre directement sur
son rôle ; aucun JDK, Maven, Docker ou code source n'est nécessaire sur les PC clients.
Les installateurs contiennent l'application compilée et ses bibliothèques, pas les fichiers source.
Le code compilé peut néanmoins être analysé : les autorisations restent vérifiées par PC1.

## Préparer PC1

Suivre d'abord la section « Première installation de PC1 » du README, notamment la préparation
de l'autorité privée et l'activation du mode réseau automatique. Garder PC1 allumé avec ses deux
services. Les clients et PC1 doivent être sur un réseau autorisant la découverte et HTTPS.

Le générateur s'adresse au **mode autorité privée réseau de PC1**, pas à un certificat serveur
historique. Récupérer uniquement `postes\secrets\autorite-ihm.crt` et son empreinte SHA-256
indépendamment, directement depuis PC1 ou via son responsable, avec par exemple sur PC1 :

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -printcert -file .\postes\secrets\autorite-ihm.crt
```

Ne jamais diffuser `configuration-PC1.local.cmd`, la clé privée CA, les `.p12`, mots de passe,
clés SMTP/NVIDIA ou la base de données. Ne jamais accepter l'empreinte d'une annonce réseau.

## Fabriquer les MSI

Sur un PC Windows x64 de fabrication (qui peut être PC1) :

1. Récupérer le projet complet et installer un **JDK 25 x64 complet** ; définir `JAVA_HOME`.
2. Installer **WiX Toolset 3** et ajouter son dossier `bin` au `PATH` pour que `candle.exe` et
   `light.exe` soient accessibles. Une connexion Internet est nécessaire si Maven et les
   dépendances ne sont pas déjà en cache.
3. Placer le certificat public hors de `target/`. Double-cliquer sur
   `outils\installateur\creer-installateurs.cmd`. Indiquer le chemin du certificat public puis
   saisir son empreinte vérifiée indépendamment. Le script refuse un certificat non CA, expiré,
   une empreinte différente ou un fichier autre que `.crt`/`.cer`.
4. Attendre la compilation, les tests Maven et la fabrication. Les trois MSI se trouvent dans
   `target\installateurs\`. Cette opération exécute `mvn clean` et supprime le contenu précédent
   de `target/` ; ne pas y conserver de données personnelles. Un échec interrompt la fabrication.

Pour un usage automatisé sur Windows, le script PowerShell accepte aussi `-CertificatPublic`
et `-EmpreinteSHA256` en paramètres. Il importe la CA publique seulement dans le runtime copié
pour les installateurs, sous un alias `plateforme-ca-…`, sans modifier le magasin du JDK de
fabrication ni désactiver la validation TLS. Le mot de passe standard du magasin de confiance
Java (`changeit`) ne protège aucune clé privée ; si le magasin de votre JDK source utilise un
mot de passe différent, la fabrication échouera. Les MSI ne sont **pas signés** : Windows peut
avertir de l'éditeur inconnu. Ne pas désactiver les protections pour masquer cet avertissement.

## Distribuer et utiliser

- Envoyer à chacun le MSI correspondant à son rôle. L'installation est par utilisateur et crée
  un raccourci dans le menu Démarrer et sur le bureau. Les trois produits peuvent coexister.
- Le certificat public vérifié est déjà intégré : les utilisateurs n'ont pas à importer la CA
  dans leur JDK système. Les MSI sont propres à l'autorité PC1 choisie lors de leur fabrication.
- Chaque personne utilise son propre compte ; l'inscription/activation et les codes e-mail restent
  ceux de la plateforme. Changer le lanceur local ne donne pas un autre rôle : PC1 contrôle le rôle.
- Après remplacement de la CA PC1, fabriquer et redistribuer les MSI avec la nouvelle CA. Pour
  les mises à jour, incrémenter la version Maven numérique avant de les reconstruire ; tester la
  mise à jour depuis la version précédente avant diffusion. La désinstallation utilise Windows.
- Les utilisateurs n'ont pas besoin d'Internet pour installer Java ou Maven ; la plateforme a
  toujours besoin d'accéder à PC1, et PC1 doit disposer des services externes nécessaires (SMTP,
  IA si activée). Si la découverte échoue, vérifier le réseau/pare-feu et saisir l'adresse de PC1
  dans l'interface, jamais `localhost` pour un autre ordinateur.

## Recette avant distribution

Sur un Windows x64 propre, sans Java ni Maven, installer chaque MSI, lancer ses raccourcis et
vérifier le rôle, puis se connecter à PC1 depuis un autre ordinateur. Tester refus d'un compte
ayant le mauvais rôle, inscription et code e-mail, redémarrage de PC1, désinstallation et mise à
jour depuis une version antérieure. Tester également une empreinte erronée à la fabrication et
une connexion à un PC1 utilisant une autre CA : elles doivent échouer. Vérifier que les MSI ne
contiennent ni sources ni secrets, et qu'aucune confiance n'a été ajoutée au JDK système. Ces
contrôles nécessitent un véritable environnement Windows, réseau et PC1 configuré.
