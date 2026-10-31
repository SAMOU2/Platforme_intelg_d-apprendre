#!/usr/bin/env bash
# Lancement générique (Linux / macOS) : sans rôle, aucun écran métier n'est accessible.
# Aucun Maven à installer : le wrapper du projet télécharge Maven au premier lancement.
# Pour ouvrir un poste, utiliser le script de rôle correspondant dans postes/.
set -euo pipefail
cd "$(dirname "$0")"

echo "JDK  : ${JAVA_HOME:-(JDK du PATH)}"
java -version 2>&1 | head -1

MVN="./mvnw"
command -v mvn >/dev/null 2>&1 && MVN="mvn"
echo "Maven: $MVN"

echo "Aucun rôle n'est fixé par ce lanceur générique."
echo "Utiliser le script correspondant dans postes/ pour démarrer un poste."
exec "$MVN" -q javafx:run
