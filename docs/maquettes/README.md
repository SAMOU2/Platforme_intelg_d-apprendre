# Maquettes de la plateforme

## Maquettes basse fidélité (dossier rapport v2.0)

L’ensemble des 12 planches couvrant tous les écrans, les 3 rôles (`Apprenant`, `Enseignant`, `Administrateur`), la cinématique `PC1`–`PC4`, l’assistant IA (`IA1`) et l’accessibilité clavier (`UX1`) est disponible en français sous deux formes complémentaires (formats `SVG` et `PNG` `1560 × 1000 px`) :

- `croquis-papier/` — **12 croquis papier** (*paper prototypes* manuscrits sur grille quadrillée, centrés sur l’exploration spatiale et la cinématique).
- `wireframes/` — **12 wireframes filaires** (niveaux de gris, centrés sur la hiérarchie, les états normalisés et la traçabilité IHM).
- Synthèse prête pour le rapport : [`../Maquettes-basse-fidelite.pdf`](../Maquettes-basse-fidelite.pdf) (source Markdown : [`../sources-v2/maquettes-basse-fidelite.md`](../sources-v2/maquettes-basse-fidelite.md)).

### Planches incluses (`00` à `11`)

| N° | Slug | Tâches HTA | Contenu |
|---|---|---|---|
| `00` | `00-cinematique-navigation` | `A0`–`UX1` | Cinématique globale des écrans (PC1–PC4) |
| `01` | `01-connexion-decouverte-confiance` | `A0` | Connexion, popup réseau PC1, OTP 6 ch., confiance 48 h, poste non configuré |
| `02` | `02-inscription-activation-pin` | `A1` | Demande de compte sans PIN, activation par PIN 8 ch., mot de passe oublié |
| `03` | `03-apprenant-parcours-niveaux` | `A2`, `A4` | Parcours par langage (`Java`/`Python`/`C++`), états et seuils |
| `04` | `04-apprenant-cours-assistant-ia` | `A2`, `IA1` | Lecture du cours (lecture seule) et assistant IA NVIDIA via PC1 |
| `05` | `05-apprenant-entrainement-ia` | `A2`, `IA1` | Entraînement local (sans exécution fictive) et aide IA sur code |
| `06` | `06-apprenant-test-final-sans-ia` | `A3` | Test final (IA bloquée serveur/client), abandon ou soumission |
| `07` | `07-apprenant-copies-resultats-tableau-bord` | `A4` | Mes copies (`SOUMISE`/`CORRIGEE`) et tableau de bord |
| `08` | `08-enseignant-niveaux-contenus` | `E1` | Gestion des niveaux en 4 étapes (`Créer`, `Cours/Exo`, `Test`, `Publier`) |
| `09` | `09-enseignant-apprenants-corrections` | `E2` | Copies affectées, brouillon privé et publication humaine de décision |
| `10` | `10-administrateur-comptes-affectations` | `AD1` | Comptes, filtres SQL, avis du bot de tri, lots bornés et affectations |
| `11` | `11-transversal-accessibilite-etats-sidebar` | `UX1` | Barre latérale réduite (`244↔76 px`), `F1/F6/F8/Échap`, 4 états et thèmes |

### Régénération

```bash
node outils/generer_maquettes_bf.mjs
python3 outils/generer_maquettes_pdf.py
```

---

# Écran de connexion (référence statique haute fidélité)

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

## Conseils animés en arrière-plan

Sur le login, six phrases de motivation et conseils de programmation flottent lentement dans
les marges, derrière le formulaire. Elles ne sont pas interactives et ne reçoivent pas le focus.
Pour préserver la lisibilité, elles disparaissent sur les petites fenêtres (moins de 1100 × 540).
Avec `-Dihm.animations=off`, elles restent immobiles sur les grands écrans. Le mouvement s'arrête
lorsque l'écran est quitté et suit l'échelle `ihm.animations.echelle`.

À vérifier sur un poste JavaFX : ouvrir le login en 1280×720 et en plein écran, redimensionner
sous le seuil puis agrandir, utiliser le formulaire et le menu réseau, aller en inscription,
se connecter et revenir ; refaire avec les animations désactivées. Les conseils doivent rester
dans les marges, sans masquer les champs ni gêner le clavier ou la souris.
