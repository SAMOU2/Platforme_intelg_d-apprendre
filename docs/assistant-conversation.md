# Assistant pédagogique plus naturel

## Changements

- Dans le fournisseur NVIDIA, les salutations simples (bonjour, salut, hello, bonsoir,
  coucou) et remerciements simples sont reconnus exactement. Ils donnent un accueil ou
  une relance avec une section du corpus réellement disponible comme point de départ,
  sans solliciter l'API NVIDIA. Les quotas applicatifs, contrôles de session et blocage
  d'évaluation restent appliqués en amont. Un « hello, valide mon niveau » n'est pas traité
  comme une simple salutation et reste soumis aux garde-fous.
- Les explications peuvent atteindre 2400 caractères au lieu de 600, avec étapes et petit
  exemple différent de l'exercice. Le schéma JSON, son générateur, le prompt et le parseur
  sont synchronisés. Les citations restent obligatoires, jamais inventées ou supprimées.
- Trois échanges réussis récents maximum sont envoyés comme données JSON avec chaque
  question (question <= 2000 caractères, réponse <= 2400). Aucun rôle système/assistant
  arbitraire n'est ajouté à la liste des messages du fournisseur. L'historique est non
  fiable et ne peut pas autoriser une notation ou publication.
- L'historique appartient uniquement au panneau de cette fenêtre et reste en mémoire ;
  il n'est ni partagé avec un autre utilisateur ni conservé après destruction du panneau.
  Il peut être réinitialisé en naviguant vers un autre écran/niveau. Les refus ne sont
  pas ajoutés au contexte de suivi. Le logiciel ne joint aucun nom ni identifiant de compte,
  mais le texte libre peut contenir ce que saisit l'utilisateur : ne pas envoyer de données
  personnelles, secrets ou contenu privé à NVIDIA.
- Les boutons d'aide ne demandent plus systématiquement une fonction ou une valeur None :
  ils demandent une piste, une explication du code courant ou la notion à revoir. En l'absence
  d'énoncé/message d'erreur précis, l'assistant doit demander une clarification.

## Protections inchangées et limites

Le test EN_COURS bloque l'IA côté PC1, y compris les salutations. Seul l'enseignant note et
valide ; ni le modèle ni le client n'ont de voie de publication automatique. Les demandes
explicites de solution complète restent refusées. Les exemples sont illustratifs, pas une
exécution de code. Quotas et disjoncteur restent actifs. La sécurité d'une réponse générée
ne repose pas uniquement sur le prompt : les filtres d'entrée/sortie existants sont conservés.

Le corpus utilisé reste le corpus standard embarqué, pas les cours rédigés par l'enseignant
stockés dans PostgreSQL. L'historique améliore le suivi mais n'apporte pas à lui seul ces
contenus. Un modèle peut encore refuser à tort : cette modification ne garantit pas toutes
les formulations. Les nouvelles limites de taille requièrent des interfaces mises à jour.
La requête IA reste bornée (64 KiB), y compris les données échappées JSON ; une saisie très
volumineuse peut donc être rejetée même si chaque champ est sous sa limite.

## Validation

Tests Java ajoutés : salutations et remerciements sans appel fournisseur, format avec
citation réelle, historique immuable et borné, transmission comme données, réponse entre
600 et 2400 caractères, refus au-delà, refus de note en sortie et maintien du blocage test.
Le test de schéma compare aussi sa longueur maximale à celle du parseur.

Compilation/JUnit et appels NVIDIA réels non exécutés dans le sandbox sans JDK/Maven.
Les contrôles Python et vérifications syntaxiques ne remplacent pas ces essais.
Recette Windows : hello → question sur une variable → « explique autrement » → « un autre
exemple » ; vérifier les citations, le code courant, les changements de panneau, deux comptes
séparés, les quotas, le refus de note/validation et le blocage pendant une vraie tentative.

## Mise à jour après publication/fusion

Aucune migration SQL, aucun changement de certificat ni de clé NVIDIA. Arrêter HTTPS et
fermer les interfaces avant mise à jour, préserver les configurations privées :

```powershell
cd "D:\S7\Platforme_intelg_d'apprendre"
git fetch origin
git merge --ff-only origin/main
.\mvnw.cmd clean test
```

Exécuter séparément, arrêter en cas d'erreur et attendre BUILD SUCCESS. Relancer le serveur
HTTPS et les interfaces mises à jour. Ne pas forcer l'écrasement des modifications locales.
