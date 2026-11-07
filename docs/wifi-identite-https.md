# Adaptation HTTPS aux changements de Wi-Fi Windows (mode explicite)

## Portée et sécurité

Ce mode optionnel utilise les **vraies adresses IPv4 actives de PC1**, sans imposer 127.0.0.1.
Une autorité privée (CA) est créée explicitement une seule fois sur PC1. Son certificat public
est approuvé une fois dans chaque JDK client après comparaison indépendante de l'empreinte.
La CA signe ensuite des certificats serveur de 30 jours avec les adresses courantes dans SAN.
Le serveur surveille les interfaces toutes les 10 secondes et renouvelle aussi après 7 jours.
Les nouvelles connexions reçoivent la nouvelle identité sans redémarrer le serveur HTTPS.

Ce n'est PAS un mode « accepter tous les certificats ». Le client HTTPS garde les contrôles
habituels de chaîne de confiance et de correspondance d'adresse. La découverte UDP n'importe
aucune CA et ne transmet aucune clé privée. Les ports sont découverts comme auparavant.
Le certificat et la clé serveur temporaires sont supprimés après chargement ; l'ancienne clé
reste en mémoire pour les négociations déjà commencées. La CA ne doit jamais quitter PC1.

L'installation est opt-in : le mode TLS historique et son magasin existant restent utilisables.
La rotation ne change ni les comptes, ni les affectations, ni les migrations, ni les copies.

## Préparer PC1 une seule fois (après publication du code)

1. Sauvegarder la configuration privée et arrêter HTTPS. Ne pas publier les sauvegardes.
2. Utiliser un JDK complet avec keytool (le Liberica JDK du projet convient).
3. Dans la configuration privée, choisir un chemin réservé à la nouvelle CA :

```bat
set "IHM_TLS_CA_KEYSTORE=%~dp0secrets\autorite-reseau.p12"
```

4. Exécuter `postes\preparer-identite-reseau-PC1.bat`. Saisir un nouveau mot de passe CA
   fort (12 caractères minimum), conservé dans un gestionnaire de mots de passe. Ne pas
   réutiliser les secrets précédemment exposés. Le script ne sauvegarde pas le secret saisi.
5. Conserver le magasin privé créé, sans le distribuer. Le script exporte uniquement le
   certificat public `postes\secrets\autorite-ihm.crt`. Ne jamais recréer la CA à chaque Wi-Fi.
   Un magasin existant provoque un refus, pas un écrasement. Si l'export seul a échoué après
   création de la CA, exporter son certificat avec keytool, ne pas supprimer la CA.
6. Restreindre l'accès au dossier privé au compte exécutant HTTPS (propriétés Windows →
   Sécurité). Protéger aussi les sauvegardes. La compromission de cette CA impose sa révocation
   manuelle sur tous les clients et son remplacement ; elle permettrait d'usurper le serveur.
7. Ajouter dans `configuration-PC1.local.cmd`, en conservant le reste :

```bat
set "IHM_TLS_RESEAU_AUTO=1"
set "IHM_TLS_CA_KEYSTORE=%~dp0secrets\autorite-reseau.p12"
set "IHM_TLS_CA_PASSWORD=VOTRE_SECRET_CA_PRIVE"
set "IHM_TLS_PASSWORD=UN_AUTRE_SECRET_TLS_PRIVE_AU_MOINS_12_CARACTERES"
set "IHM_AUTH_PORT=0"
```

Les secrets ci-dessus sont des exemples à remplacer localement, jamais à publier. En CMD,
écrire un caractère pourcent deux fois dans un secret ; ne pas ajouter setlocal au fichier
local. Le nouveau IHM_TLS_PASSWORD protège les magasins générés : il n'est pas appliqué au
magasin historique. Garder le mot de passe historique séparément pour un éventuel retour
au mode précédent. Aucune modification du fichier privé n'est faite automatiquement.

## Approuver la CA sur chaque poste client

Sur PC1, afficher l'empreinte du certificat PUBLIC avec keytool :

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -printcert -file .\postes\secrets\autorite-ihm.crt
```

Transférer seulement `autorite-ihm.crt` aux postes, jamais la CA .p12 ni ses secrets.
Sur chaque JDK utilisé par les lanceurs, exécuter `postes\approuver-autorite-client.bat`
avec les permissions nécessaires pour modifier son cacerts (souvent administrateur).
Le script demande le chemin du certificat public, refuse un .p12, contrôle le statut CA,
puis exige de saisir l'empreinte SHA256 obtenue indépendamment sur PC1/par son responsable.
Keytool demande ensuite le mot de passe du magasin de confiance et confirmation. Vérifier
sa confirmation d'ajout. Aucun mot de passe n'est dans la ligne de commande. Une erreur ou
un alias déjà présent n'autorise pas la suppression d'entrées au hasard.

Sur un seul PC utilisant le même JDK pour tous les rôles, une seule approbation suffit.
Le certificat autosigné historique déjà approuvé n'est PAS cette nouvelle CA. Il ne suffit
pas d'avoir importé l'ancien serveur.crt. Ne jamais approuver une CA reçue d'une annonce Wi-Fi.
Mettre à jour un JDK peut nécessiter de réinstaller la confiance dans ce nouveau JDK.

## Pourquoi cela marche sur n'importe quel Wi-Fi

L'adresse du serveur change à chaque réseau (192.168.x.x chez l'un, 10.x.x.x ailleurs, partage
de connexion d'un téléphone…). Un contrôle « l'adresse doit figurer dans le certificat », celui
du Web public, casserait la connexion après chaque changement de réseau. La plateforme fait donc
reposer la confiance sur **l'autorité privée approuvée à la main sur chaque JDK**, pas sur
l'adresse : le client accepte un certificat signé par cette autorité, quelle que soit l'adresse
du serveur. Concrètement :

- le serveur renouvelle son certificat dès que ses adresses changent (vérification toutes les
  10 s) ; les adresses inscrites restent utiles pour les navigateurs et les outils de diagnostic ;
- le client ne fait confiance **qu'aux entrées `plateforme-ca-*`** du magasin du JDK, jamais aux
  autorités publiques, et jamais à une autorité non approuvée ;
- la vérification TLS n'est jamais désactivée : la chaîne est validée, seul le contrôle du nom,
  inapplicable sans DNS sur un LAN, est écarté ;
- à défaut d'entrée `plateforme-ca-*` (autorité importée sous un autre alias, ou certificat
  approuvé avant la mise en place de l'autorité), la chaîne est validée avec le magasin
  complet du JDK, toujours sans exiger le nom. Une autorité publique ne peut pas délivrer de
  certificat pour une adresse privée (192.168.x.x, 10.x.x.x) : elle n'est donc pas une voie
  d'usurpation sur le réseau local. Si aucun magasin n'est lisible, le contrôle strict est
  conservé et rien n'est contourné silencieusement.

Changer de Wi-Fi ne demande donc aucune manipulation de certificat : garder la fenêtre
d'authentification ouverte une dizaine de secondes, laisser la découverte retrouver le serveur,
puis se connecter.

## Mettre à jour et relancer en un seul geste

`postes\maj-et-relancer-PC1.cmd` (double-clic, ou `.\postes\maj-et-relancer-PC1.cmd` depuis
PowerShell — le `./` est obligatoire, sinon PowerShell croit que `postes` est un module)
enchaîne : fermeture des fenêtres du projet,
`git fetch` + `git merge --ff-only origin/main`, compilation, puis relance de l'authentification,
du serveur de salle et de PC4, en attendant que le port HTTPS soit annoncé avant d'ouvrir les
clients. Le script s'arrête au premier échec (branche autre que `main`, modifications locales,
compilation impossible, aucun port annoncé) en disant quoi faire, et ne supprime aucune donnée.
Une session en cours est interrompue : c'est le but d'une relance complète.

## Vérifier l'accessibilité du certificat

Avant toute autre hypothèse, lancer `postes\verifier-certificat-https.cmd` (double-clic ou
depuis la racine du projet). Il n'ouvre aucune session et ne modifie aucun magasin : il lit.
Il affiche l'empreinte de la CA présente dans `postes\secrets`, la présence d'une entrée
`plateforme-ca-*` dans le JDK qui lance l'application, puis le certificat réellement présenté
par le serveur : sujet, émetteur, dates de validité et adresses couvertes (SAN). Il indique
enfin si ce JDK approuve le certificat à l'adresse et au port saisis (le port courant est
proposé automatiquement d'après `.ihm-runtime\https.port`).

Attention : ce diagnostic utilise les contrôles stricts du JDK (chaîne **et** nom), alors que
l'application, elle, se fie à l'autorité privée sans exiger le nom (voir ci-dessus). Un refus de
nom dans le diagnostic n'annonce donc pas forcément un échec de l'application.

Si le message de l'application indique le port **8443**, aucun port HTTPS n'a été annoncé :
c'est la valeur de repli historique, jamais le port réel. Vérifier que la fenêtre
d'authentification est bien ouverte (elle publie son port dans `.ihm-runtime\https.port` et
dans `%USERPROFILE%\.ihm2026-reseau`), puis cliquer sur « Réessayer ».

Interprétation : échec de confiance avec un SAN qui ne contient pas l'adresse utilisée ⇒
certificat non encore renouvelé pour le réseau courant (attendre la rotation) ou mauvaise
adresse ; échec sans certificat ⇒ port/adresse erronés, service arrêté ou pare-feu. Un code
HTTP 404 en GET sur `/api/auth/connexion` est normal et prouve que TLS est négocié.

## Démarrage et changements de réseau

Après BUILD SUCCESS, relancer HTTPS, le serveur de salle et les interfaces à jour, en une
seule instance chacun. Le terminal HTTPS doit afficher `[tls-reseau] Certificat actualise`.
Le serveur de salle continue à annoncer le port HTTPS actuel. Les interfaces ignorent la
boucle locale, les cartes virtuelles de l'hôte et les tunnels (vEthernet WSL/Hyper-V,
Docker, VMware, VirtualBox, Cloudflare WARP et VPN) pour la découverte comme pour les
adresses du certificat ; elles ne remplacent pas une IP LAN par 127.0.0.1.

L'écran de connexion relance périodiquement la découverte. En mode adresse manuelle
(Options avancées ouvertes avec une adresse saisie), l'adresse reste celle de l'utilisateur :
la recherche ne l'écrase jamais, elle se contente d'actualiser le port HTTPS annoncé pour
cette même adresse. Les recherches se suspendent pendant la connexion/OTP. Aucun nom, mot de
passe, OTP ou formulaire n'est effacé par une recherche en fond.

Le port HTTPS est **entièrement automatique** : les lanceurs PC1 demandent le port 0, donc un
port libre choisi au démarrage, publié dans le registre puis annoncé aux postes par la
découverte (UDP 7777). Rien n'est à saisir sur les clients, même avec une adresse écrite à la
main : la découverte actualise le port sans jamais écraser l'adresse. La valeur 8443 n'est
qu'un marqueur : si elle apparaît à l'écran, c'est qu'aucun port n'a été annoncé, et le
message le dit.
Après un changement de Wi-Fi, attendre la nouvelle annonce et la rotation du certificat
(environ 10 secondes plus le temps de signature). Une tentative trop précoce peut encore
échouer : réessayer après le message de rotation. Ne pas changer de CA pour cette erreur.

**Les sessions existantes ne migrent pas automatiquement vers une autre IP.** En cas de
coupure pendant un travail, ne pas fermer la fenêtre ni resoumettre aveuglément. Copier le
brouillon non soumis, puis reconnecter au serveur découvert. Les réponses non soumises ne
sont toujours pas sauvegardées sur PC1. Ce changement ne prétend pas résoudre la reprise
transactionnelle de chaque écran après coupure réseau.

Si aucune adresse IPv4 physique n'est active au démarrage, le mode automatique refuse de
servir une identité vide. En cours d'exécution, un échec de renouvellement conserve le dernier
certificat et signale une erreur, sans désactiver TLS. La CA doit rester valide plus de 31 jours.
Les IPv6, DNS stables multi-réseaux, VPN particuliers et renouvellement de la CA ne sont pas
gérés automatiquement dans cette première version. Les alias de clés de rotation sont
conservés en mémoire jusqu'au redémarrage ; pour un service de très longue durée, prévoir
un redémarrage de maintenance.

## Réseaux publics et erreurs

Un Wi-Fi d'université peut isoler les postes ou bloquer UDP, les connexions entrantes, SMTP
ou NVIDIA. Aucune rotation de certificat ne contourne ces restrictions. Une découverte vide
ne prouve pas à elle seule un isolement : vérifier que PC1 est démarré, le profil pare-feu,
les règles de l'établissement et si besoin l'adresse/port manuels. Ne pas désactiver le
pare-feu ni TLS, ne pas ajouter arbitrairement une CA inconnue. Windows gère le Wi-Fi et ses
mots de passe ; la plateforme ne les collecte pas.

## Déploiement et vérifications

Pas de migration SQL. Mise à jour après publication/fusion, commandes séparées :

```powershell
cd "D:\S7\Platforme_intelg_d'apprendre"
git fetch origin
git merge --ff-only origin/main
.\mvnw.cmd clean test
```

Arrêter les serveurs avant mise à jour. Préserver les modifications locales, les magasins et
la configuration privée. Le test crypto conditionnel peut être exécuté sur une base vide de
secrets réels, sans PostgreSQL :

```powershell
$env:IHM_TEST_TLS_RESEAU="1"
.\mvnw.cmd -Dtest=IdentiteTlsReseauTest test
Remove-Item Env:IHM_TEST_TLS_RESEAU
```

Il crée une CA temporaire et vérifie signature, SAN, handshake HTTPS avec confiance CA,
refus de nom non couvert, renouvellement et conservation de l'ancien certificat si aucune
adresse n'est disponible. Compilation/JUnit/Windows non exécutés dans le sandbox : analyses
syntaxiques et contrôles Python ne prouvent pas le fonctionnement en exécution.

Recette obligatoire sur Windows : installation explicite CA, approbation/refus d'empreinte,
connexion de deux PC, changement d'IP/Wi-Fi, SAN renouvelé, redécouverte de l'adresse/port,
reconnexion OTP, échec sûr avec CA non approuvée, dossier privé inaccessible ou réseau filtré,
retour au mode historique. Ne pas activer largement sans cette validation réelle.
