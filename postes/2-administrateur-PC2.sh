#!/usr/bin/env bash
# =====================================================================
#  POSTE 2 (PC2) : ADMINISTRATEUR
#  Equivalence Linux/macOS de postes/2-administrateur-PC2.cmd (la salle est sous Windows ; ce
#  fichier sert aux tests et aux postes de developpement).
#  Le serveur est decouvert automatiquement ; le PIN individuel est saisi a l'inscription.
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"
MVN=./mvnw
PORT="${PORT:-7777}"
ARGS="--role=administrateur --poste=PC2 --port=${PORT}"
echo "  PC2 administrateur - detection automatique du serveur local."
exec "$MVN" -B javafx:run "-Djavafx.args=${ARGS}"
