# NVIDIA Nemotron via PC1

Modèle : `nvidia/nemotron-3.5-bolt-e4b`.
Point de sortie fixe : `https://integrate.api.nvidia.com/v1/chat/completions`.
Aucun téléchargement de modèle, aucune dépendance Python côté utilisateur.

## Architecture

Interface authentifiée → POST HTTPS `/api/ia/aide` sur PC1 → NVIDIA.
Les interfaces utilisent leur jeton de compte existant. La clé fournisseur reste dans
le processus du service HTTPS, jamais dans les réponses, les logs ou la base.
Le service de salle 7777 ne transmet pas de questions à NVIDIA.

Seul le démarrage du service HTTPS installe le fournisseur NVIDIA. Les interfaces
sans session continuent d'avoir une IA désactivée ; la configuration ancienne du
connecteur distant ne correspond pas à cette intégration.

## Configuration Windows après publication et mise à jour

1. Fermer les interfaces, arrêter uniquement HTTPS par Ctrl+C, conserver PostgreSQL,
   certificats, sauvegardes et serveur de salle.
2. Récupérer la mise à jour et exécuter ` .\mvnw.cmd clean test`.
3. Éditer **uniquement** `postes\configuration-PC1.local.cmd` (ignoré par Git), et
   ajouter avant son éventuel `exit /b` :

   ```cmd
   set "NVIDIA_API_KEY=VOTRE_CLE_PRIVEE"
   ```

   Ne pas remplacer les paramètres existants. Ne pas modifier le fichier exemple
   avec une vraie clé et ne jamais la coller dans la conversation. La variable peut
   aussi être définie dans l'environnement du processus serveur.
4. Lancer `postes\authentification-PC1.bat` une seule fois, puis les clients habituels.
5. Se connecter avec OTP et ouvrir l'assistant dans Cours/Exercice.

Le fichier exemple contient seulement une ligne commentée de documentation.
Il n'est pas nécessaire d'avoir une clé pour exécuter les tests unitaires.

## Comportement

- Appel JSON sans streaming (`stream:false`), réflexion désactivée, température 0,2,
  maximum 1024 tokens, délai fournisseur 45 s ; client PC1 55 s.
- Jusqu'à quatre extraits embarqués du langage demandé sont classés lexicalement
  selon la question ; ils sont joints au prompt et à la demande, sans identité de compte.
- Le format `system-v2` est validé : texte court, citations structurées, refus connus.
  Chaque citation doit appartenir aux extraits envoyés ; aucune sortie libre ou
  raisonnement interne n'est affiché. Cela ne garantit pas la justesse pédagogique :
  une évaluation humaine reste nécessaire.
- Garde-fous d'entrée et de sortie conservés : pas de notes ni de validation de niveau.
- Appels JavaFX hors du thread graphique, commandes du panneau bloquées pendant l'attente.
- Authentification obligatoire ; limites serveur : 30 demandes par compte/jour,
  200 appels admissibles partagés/jour ; une requête IA à la fois, sinon HTTP 429.
  Compteurs en mémoire, remis à zéro au redémarrage et à la date locale suivante.
  Le disjoncteur existant s'applique aux échecs répétés. Aucun retry automatique.
- Clé absente, HTTP 401/429/5xx, timeout ou JSON invalide : indisponibilité sans secret.

## Confidentialité et limites

NVIDIA reçoit **la question, le code éventuellement saisi et des extraits de cours**.
Aucun nom, email, jeton de connexion ou mot de passe n'est ajouté par l'application.
Un utilisateur peut néanmoins saisir lui-même des données privées dans son texte :
elles ne sont pas automatiquement anonymisées. L'interface avertit de ne pas le faire.
Vérifier les conditions, quotas et règles de journalisation NVIDIA avant utilisation
réelle ; le badge Free Endpoint n'est pas une garantie de gratuité illimitée.

Le blocage des tests de validation repose encore sur l'état de session du client
existant ; PC1 ne dispose pas d'un registre central autoritaire des évaluations.
Un client modifié pourrait donc contourner cette partie. Ce mécanisme ne constitue
pas un contrôle anti-triche serveur complet. Ne pas déployer pour des examens à enjeu
sans un suivi d'évaluation centralisé.

## Vérifications

Tests sans réseau dans `ModeleNvidiaTest` : requête/enveloppe NVIDIA, absence de clé,
erreurs 401/429/500, timeout, JSON invalide et citations inventées.

Recette Windows nécessaire : compilation et JUnit, HTTPS sans session (401), mauvais
contexte (400), saturation/quota (429), clé absente/invalide, vraie clé, réponse à
« Explique les variables en Python », refus de « donne-moi la solution complète »,
connexion depuis un autre PC sans clé fournisseur, interface réactive pendant l'attente.

Dans le sandbox : aucun appel réel NVIDIA (pas de clé et hôte non autorisé), aucun
JDK/Maven disponible ; les contrôles Python et l'analyse syntaxique ne remplacent
pas compilation, JUnit, PostgreSQL, JavaFX ou validation des réponses réelles.

## Diagnostic de l'indisponibilité

La console HTTPS indique maintenant au démarrage seulement si la clé est PRESENTE
ou ABSENTE (pas sa valeur ni sa longueur). PRESENTE ne prouve pas sa validité.
Après une demande, les codes `[ia-nvidia]` distinguent HTTP_401, HTTP_403, HTTP_429,
DELAI_DEPASSE, FORMAT_INVALIDE, CITATION_INVALIDE et CORPUS_ABSENT. Les corps de
réponse et les messages d'exception du fournisseur ne sont jamais imprimés.
La console client indique `[ia-pc1] HTTP ...` si PC1 refuse la requête, sans jeton
ni corps de réponse. Une erreur 404 côté PC1 peut indiquer un ancien serveur HTTPS
non redémarré après mise à jour. L'interface conserve un message générique.
Ces diagnostics ne corrigent pas à eux seuls la cause de la panne ; ils permettent
de la déterminer sans partager de secrets.
