# Première base d'accessibilité clavier et lecteur d'écran

Cette amélioration vise notamment les personnes aveugles utilisant un lecteur d'écran,
ainsi que les personnes malvoyantes ou ne pouvant pas utiliser une souris. **Ce n'est
pas une certification WCAG/RGAA ni une garantie que toute la plateforme est accessible.**

## Ce qui change

- Navigation latérale et choix Python/Java/C++ : vrais boutons JavaFX au lieu de lignes
  utilisables uniquement à la souris. Activation native par clavier ou lecteur d'écran.
  L'écran actif et le parcours sélectionné figurent dans le nom accessible.
- Cartes de niveaux communes : nom contenant rang, titre, état, explication, seuil et
  tentatives. Si elles ont une action, elles acceptent Entrée/Espace et l'action
  d'accessibilité FIRE. Leur action métier et les contrôles d'autorisation ne changent pas.
- Boutons à icône de la barre de titre et déconnexion : noms explicites.
- Formulaire de connexion : noms explicites des champs, y compris OTP, PIN, confirmation,
  case « Faire confiance à ce poste pendant 2 jours », bouton d'oubli du poste et port de
  secours. Les états de connexion/découverte sont atteignables par Tab.
- Champs de réponse du test serveur : énoncé associé au champ ou au choix QCM. Aucun
  corrigé ni indice supplémentaire n'est introduit. Console, éditeurs/brouillon et
  champ de question IA nommés.
- Mot de passe : toujours masqué par défaut. Le bouton Afficher/Masquer indique quel
  champ il concerne ; le champ en clair indique son état, sans incorporer la valeur du
  secret dans son nom accessible. Révéler reste une action explicite de l'utilisateur.
- Contour de focus en pointillés sur la navigation et les cartes communes, indépendant
  de leur couleur de statut, dans les thèmes clair et sombre. Contraste à vérifier en recette.
- À un changement d'écran, focus sur la région de contenu nommée. Pas de nouvelle prise
  de focus déclenchée par le simple rafraîchissement automatique des données.

## Raccourcis

| Touche | Action |
|---|---|
| Tab / Maj + Tab | Commande suivante / précédente (comportement JavaFX natif) |
| F6 / Maj + F6 | Région suivante / précédente : commandes de fenêtre, navigation visible, contenu |
| F1 | Aide locale ; aussi disponible via le bouton « Aide clavier » |
| F8 | Relecture explicite de la dernière notification dans une boîte de dialogue |
| Entrée / Espace | Activer un bouton ; saisir normalement dans les champs de texte |
| Échap | Fermer l'aide ou la relecture |

Les combinaisons Ctrl/Alt/Meta ne sont pas interceptées par ces nouveaux raccourcis.
Le texte de la dernière notification reste uniquement en mémoire sur la racine de la
scène : pas de fichier, pas d'envoi vocal externe, pas d'historique global par compte.
Il est effacé au retour à la connexion/écran public, et le popup de cette fenêtre est
fermé, pour ne pas transmettre un ancien message à l'utilisateur suivant. Une notification
ne vole pas automatiquement le focus : utiliser F8 si elle a disparu.

Les raccourcis ne soumettent jamais une copie et ne déclenchent pas d'aide IA. Le blocage
IA pendant les tests et la correction/publication par l'enseignant restent inchangés.

## Recette Windows avec NVDA — à effectuer avant de conclure à la compatibilité

Utiliser le JDK/JavaFX réellement employé pour lancer le projet et une version récente de
NVDA. Les API accessibles JavaFX ne suffisent pas à prouver que NVDA annonce le bon texte.

1. Démarrer NVDA avant le client ; se servir du visualiseur de parole pour noter les annonces.
2. Sans souris, faire Tab/Maj+Tab et F6/Maj+F6 sur la connexion : chaque champ, bouton,
   état et plateforme doit être atteignable, nommé et quitter le focus sans piège.
3. Vérifier mot de passe et confirmation : NVDA ne doit pas lire le secret masqué. Tester
   Afficher/Masquer volontairement ; noms distincts, position du curseur et focus cohérents.
4. Tester demande de compte, activation PIN et connexion OTP ; les champs invisibles ou
   désactivés ne doivent pas être visités. Vérifier les messages d'erreur sans souris.
5. Pour les trois rôles : parcourir la navigation, changer de langage, revenir au contenu
   avec F6. Vérifier l'annonce de l'écran et du parcours sélectionnés ; barre masquée ignorée.
6. Tester une carte de niveau avec Entrée, Espace et la commande d'activation NVDA ; comparer
   l'action à la souris. Une carte non actionnable ne doit pas être annoncée comme un bouton.
7. Déclencher une notification puis attendre sa disparition ; F8 doit permettre de la lire
   et de revenir au contrôle précédent. Se déconnecter : F8 ne doit pas relire l'ancien message.
8. En entraînement, saisir/lire du code et lire la console. Vérifier l'indentation avec les
   réglages de lecture de ponctuation de NVDA. Pas de déclaration de compatibilité d'éditeur
   tant que cet essai n'est pas concluant.
9. Pendant un vrai test, lire chaque énoncé et choix sans souris, changer les réponses,
   annuler la confirmation de soumission puis soumettre volontairement. Vérifier que l'IA
   reste bloquée et qu'aucune note/publication automatique n'apparaît.
10. Vérifier clair/sombre et rétro, zoom système, focus visible, défilement des longs contenus,
    ordre de tabulation, fenêtres modales, tableaux enseignants/admin et actualisation asynchrone.

**À poursuivre après ces essais :** titres/structure des cours riches, tables complexes,
annonces automatiques des statuts, couverture complète des formulaires enseignants/admin,
ordre de lecture et retour de focus des boîtes de dialogue. Des personnes aveugles doivent
participer à la recette ; une capture PNG seule ne prouve pas l'accessibilité.

## Contrôles techniques

`python -m unittest discover -s outils/tests -p 'test_*.py'` comprend six contrôles structurels
dédiés à cette base. Ils ne simulent ni les événements JavaFX ni NVDA. Vérifier également la
compilation et les tests avec ` .\mvnw.cmd clean test` sur Windows, puis la recette ci-dessus.
Aucune modification de base de données, de certificat ou de secret n'est nécessaire.
