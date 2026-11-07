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
