# Lisibilité et retours utilisateur communs

- Contrôles éditables, menus, listes et tableaux : teintes explicites en mode sombre,
  texte de saisie clair, indications secondaires lisibles, sélection contrastée.
- Dialogues partagés et création de compte : feuilles de style du propriétaire.
- Barre de titre personnalisée : rouge/jaune/vert, symboles permanents et libellés
  accessibles, pastilles de 22 px navigables au clavier. La barre native Windows
  éventuelle n'est pas recolorée par ces règles JavaFX.
- Mots de passe de connexion, inscription/confirmation et création de compte admin :
  bouton Afficher/Masquer. Le PasswordField reste la source de vérité, les effacements
  et changements de mode existants s'appliquent aux deux vues. Aucun stockage ajouté.
- Notifications communes non modales : fermeture explicite, succès/informations
  pendant huit secondes, erreurs persistantes ; une notification visible à la fois.
  Les erreurs des tâches asynchrones, changements de thème, confirmations informatives,
  validations et succès de demande de compte utilisent ces retours communs.

Il ne s'agit pas encore d'un audit exhaustif de chaque action métier : les messages
inline et confirmations existants restent présents. Les notifications ne sont ni des
notifications système Windows ni des emails ; pas de nouvelle permission requise.

## Recette Windows à réaliser

1. Connexion, inscription et création admin : saisir un secret de test, afficher/masquer,
   effacer, vérifier confirmation et envoi ; changement de mode remet le secret masqué.
2. Clair/sombre : saisir dans les champs, ouvrir listes/menus/tableaux/dialogues,
   vérifier contraste, sélection, placeholder et commandes désactivées.
3. Tester réduction, agrandissement/restauration et fermeture ; clavier et tooltips.
4. Déclencher validation de formulaire, succès et erreur asynchrone dans chaque rôle ;
   fermer une notification, vérifier que les confirmations critiques restent modales.
5. Vérifier petites résolutions, mise à l'échelle Windows et style rétro.

Pas de capture JavaFX ni d'exécution Windows dans le sandbox sans JDK/Maven.
Les contrôles Python et l'analyse syntaxique ne remplacent pas cette recette.
