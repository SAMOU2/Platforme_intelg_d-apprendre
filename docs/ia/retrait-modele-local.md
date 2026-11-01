> Mise à jour : le connecteur NVIDIA est maintenant intégré au service HTTPS de PC1.
> Sans clé, il reste indisponible. Voir [le guide NVIDIA](nvidia-nemotron.md).
> Le texte ci-dessous décrit le retrait initial des modèles locaux.

# Retrait des modèles locaux

## Supprimé
- Poids embarqués `modele-local-v1.json` et `modele-local-v2.json`.
- Classe `ModeleLocal`, tests spécifiques, dossier d'entraînement `ia/modele`.
- Ressource Maven copiant les poids de l'entraînement dans le classpath.
- Sélection locale par défaut et repli automatique vers un simulateur.

## Conservé
- Corpus pédagogique, prompts, schéma des réponses, garde-fous et politique de disponibilité.
- Tables lexicales extraites sans changement du v1 dans `tokeniseur.json` ; ce fichier
  ne contient ni poids d'apprentissage ni modèle génératif.
- `ModeleSimule`, utilisé par les tests déterministes et pour partager le corpus/prompt :
  il n'est plus sélectionnable via la configuration de démarrage.
- Ancien connecteur distant, explicitement sélectionnable, non compatible NVIDIA en l'état.
- `version.properties` et les paramètres de stratégie historiques annotés.

Par défaut, `ModeleIndisponible` renvoie `INDISPONIBLE`, sans réseau ni modèle local.
L'intégration NVIDIA, ses clés côté serveur et son routage réseau restent un travail séparé.
Les anciennes captures, rapports d'évaluation et entrées de changelog ne décrivent pas
le fournisseur actuellement actif.

## Windows après mise à jour
Fermer les interfaces puis exécuter `git fetch origin`, `git merge --ff-only origin/main`
et ` .\mvnw.cmd clean test`. Le `clean` supprime aussi les anciens poids dans `target/classes`.
Ne jamais supprimer les configurations privées, certificats, comptes ou sauvegardes.
Aucun fichier de `D:\GhidraAI` et aucun logiciel llama.cpp n'est modifié par ce changement.

Les contrôles Python ne remplacent pas la compilation Java/JUnit ni les essais Windows.
