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
2. Double-cliquer sur `outils\installateur\creer-installateurs.cmd`. Le script recherche
   WiX Toolset 3 dans son emplacement habituel, dans `WIX`, puis dans le `PATH`. Si aucun WiX
   n'est trouvé, il télécharge automatiquement l'archive officielle **WiX 3.14.1** (environ
   40 Mo) et contrôle son empreinte SHA-256 avant toute extraction. Aucune installation globale,
   modification permanente du `PATH` ou permission administrateur n'est nécessaire pour WiX.
   Le fichier vérifié est conservé dans `outils\installateur\.wix\` (ignoré par Git), puis
   recontrôlé et extrait dans un dossier temporaire à chaque fabrication. Une connexion Internet
   est nécessaire au premier téléchargement, ainsi que pour Maven si ses dépendances ne sont pas
   déjà en cache. Si le téléchargement échoue, vérifier l'accès à GitHub ; pour un WiX installé
   ailleurs, lancer le script PowerShell avec `-WixBin "chemin-vers-le-dossier-bin"`. L'option
   `-SansTelechargementWiX` interdit tout téléchargement ; `-ForcerWiXIntegre` ignore les WiX
   installés et utilise le cache ou télécharge l'archive officielle.
3. Placer le certificat public hors de `target/`. Quand le programme le demande, sur PC1,
   appuyer sur Entrée pour accepter
   le certificat local proposé ; sinon indiquer le chemin d'une copie du certificat public de PC1.
   Saisir ensuite son empreinte vérifiée indépendamment. Le script refuse un certificat non CA, expiré,
   une empreinte différente ou un fichier autre que `.crt`/`.cer`.
4. Attendre la compilation, les tests Maven et la fabrication. Les trois MSI se trouvent dans
   `target\installateurs\`. Cette opération exécute `mvn clean` et supprime le contenu précédent
   de `target/` ; ne pas y conserver de données personnelles. Un échec interrompt la fabrication.
   Si le cache WiX est signalé corrompu, supprimer seulement le fichier
   `outils\installateur\.wix\wix314-binaries.zip` puis relancer pour le télécharger à nouveau.
   `SHA256SUMS.txt` permet de vérifier que les MSI n'ont pas été altérés pendant leur transfert ;
   communiquer les empreintes par un canal sûr (ce fichier ne remplace pas une signature).

Pour un usage automatisé sur Windows, le script PowerShell accepte aussi `-CertificatPublic`
et `-EmpreinteSHA256` en paramètres. Il importe la CA publique seulement dans le runtime copié
pour les installateurs, sous un alias `plateforme-ca-…`, sans modifier le magasin du JDK de
fabrication ni désactiver la validation TLS. Le mot de passe standard du magasin de confiance
Java (`changeit`) ne protège aucune clé privée ; si le magasin de votre JDK source utilise un
mot de passe différent, la fabrication échouera. Les MSI ne sont **pas signés** : Windows peut
avertir de l'éditeur inconnu. Ne pas désactiver les protections pour masquer cet avertissement.

## Fabrication facultative par GitHub Actions

Si le dépôt est sur GitHub et que les Actions sont activées, le workflow **Installateurs Windows**
peut fabriquer les MSI sans installer WiX ou Java sur ton PC. Le workflow doit d'abord être présent
sur la branche par défaut. Dans l'onglet **Actions**, ouvrir ce workflow, choisir **Run workflow**
et la branche à construire. Les deux champs demandent uniquement :

1. Le contenu **public** du fichier `.crt` ou `.cer` de la CA PC1, encodé en Base64. Sur PC1,
   dans PowerShell à la racine du projet, copier ce texte dans le presse-papiers avec :

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\postes\secrets\autorite-ihm.crt).Path)) | Set-Clipboard
   ```

2. L'empreinte SHA-256 du certificat, affichée séparément sur PC1 avec la commande `keytool`
   indiquée plus haut, puis vérifiée. Une empreinte incorrecte bloque la fabrication.

Après réussite, télécharger l'artefact `installateurs-windows-pc1` dans la page de l'exécution :
son ZIP contient les trois MSI et leurs sommes SHA-256. L'artefact est conservé 14 jours et reste
accessible selon les droits du dépôt. Ne jamais saisir de fichier `.p12`, clé privée, mot de passe
ou configuration serveur dans les champs du workflow : leurs valeurs peuvent être visibles aux
personnes ayant accès à l'exécution. Les MSI restent liés à la CA publique fournie. Les contrôles
automatiques testent aussi le refus d'un cache WiX corrompu, puis installent, inspectent et
désinstallent les trois postes sur Windows ; ils ne remplacent
pas un essai graphique ni une connexion réelle à PC1. Pour les pull requests, une CA **jetable**
est utilisée uniquement pour les tests et les MSI correspondants ne sont pas publiés.

## Distribuer et utiliser

- Envoyer à chacun le MSI correspondant à son rôle. L'installation est par utilisateur et crée
  un raccourci dans le menu Démarrer et sur le bureau. Les trois produits peuvent coexister dans
  `%LOCALAPPDATA%\Programs`, chacun dans son propre dossier.
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

Sur une machine Windows x64 vierge de test, le script PowerShell
`outils\installateur\tester-installateurs.ps1` permet de tester les trois MSI générés, en lui
fournissant l'empreinte SHA-256 vérifiée de la CA par le paramètre `-EmpreinteSHA256`. Il installe
et désinstalle réellement les postes : ne pas l'exécuter sur un PC déjà utilisé pour ces rôles.

Sur un Windows x64 propre, sans Java ni Maven, installer chaque MSI, lancer ses raccourcis et
vérifier le rôle, puis se connecter à PC1 depuis un autre ordinateur. Tester refus d'un compte
ayant le mauvais rôle, inscription et code e-mail, redémarrage de PC1, désinstallation et mise à
jour depuis une version antérieure. Tester également une empreinte erronée à la fabrication et
une connexion à un PC1 utilisant une autre CA : elles doivent échouer. Vérifier que les MSI ne
contiennent ni sources ni secrets, et qu'aucune confiance n'a été ajoutée au JDK système. Ces
contrôles nécessitent un véritable environnement Windows, réseau et PC1 configuré.
