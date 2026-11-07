# Écran de connexion

`proposition-connexion.svg` est la référence visuelle statique. L'écran JavaFX reprend
la carte bleu nuit sans marque nominative, tout en conservant les commandes réelles :
connexion, inscription (hors administrateur), activation, code e-mail, confiance et
oubli du poste. Le bouton réseau ouvre toujours la découverte, l'adresse et le port HTTPS.
Le statut de découverte correspond à l'état réel ; la maquette seule n'est pas interactive.

## Recette manuelle

Sur un poste avec JDK 25 et JavaFX, démarrer le rôle apprenant, enseignant puis
administrateur. Vérifier à chaque fois le rendu et la navigation au clavier :

- saisir e-mail/mot de passe, afficher puis masquer le mot de passe et se connecter ;
- vérifier les messages d'erreur, puis le champ de code e-mail si un code est requis ;
- ouvrir et fermer l'aide, créer un compte, revenir en arrière, activer par PIN ;
- cocher la confiance et utiliser « Oublier ce poste » avec le compte correspondant ;
- ouvrir les paramètres réseau, sélectionner une plateforme ou saisir adresse et port ;
- tester à 1280×720 et sur une fenêtre réduite : défiler pour atteindre chaque commande.

Ces vérifications nécessitent un service HTTPS et une base de test configurés sur PC1 ; ne
jamais utiliser de mot de passe, code ou jeton réel dans une capture ou un rapport.

## Transition connexion / inscription

« Créer un compte » fait apparaître le formulaire d'inscription par la droite avec un léger
fondu ; « J'ai déjà un compte » revient par la gauche. Le mode et les champs changent sans
attendre la fin de la `Timeline`, et le focus passe au premier champ utile. Les clics rapides
interrompent la transition précédente. Avec `-Dihm.animations=off`, le changement est immédiat.
La durée suit `ihm.animations.echelle` comme les autres animations de l'application.

À vérifier sur les postes : allers-retours rapides, navigation Tab/Entrée, petits écrans avec
défilement, inscription, activation et saisie de code, puis même parcours avec les animations
désactivées. Les changements de mode ne déclenchent aucune requête réseau supplémentaire.

## Apparence du login

Le login et l'écran de poste non configuré utilisent toujours la palette sombre ; leur bouton
clair/sombre est masqué. Après connexion, le bouton réapparaît et la préférence d'apparence
précédente (ou la valeur de `-Dihm.theme`) est restaurée. Le bouton de style rétro et les autres
commandes de fenêtre restent indépendants. Vérifier le passage login → application → déconnexion
avec une préférence claire puis sombre, ainsi que la lisibilité du menu réseau et des dialogues.
