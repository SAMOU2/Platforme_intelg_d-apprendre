#!/usr/bin/env bash
# =====================================================================
#  POSTE 4 (PC4) : APPRENANT
#  Equivalence Linux/macOS de postes/4-apprenant-PC4.cmd (la salle est sous Windows ; ce
#  fichier sert aux tests et aux postes de developpement).
#  Le serveur est decouvert automatiquement ; le PIN individuel est saisi a l'inscription.
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"
MVN=./mvnw
PORT="${PORT:-7777}"
ARGS="--role=apprenant --poste=PC4 --port=${PORT}"
echo "  PC4 apprenant - detection automatique du serveur local."
exec "$MVN" -B javafx:run "-Djavafx.args=${ARGS}"
