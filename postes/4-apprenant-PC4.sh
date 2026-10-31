#!/usr/bin/env bash
# =====================================================================
#  POSTE 4 (PC4) : APPRENANT
#  Equivalence Linux/macOS de postes/4-apprenant-PC4.cmd (la salle est sous Windows ; ce
#  fichier sert aux tests et aux postes de developpement).
#  Le serveur est decouvert automatiquement ; le PIN individuel est saisi a l'inscription.
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
MVN=./mvnw
PORT="${PORT:-7777}"
ARGS="--role=apprenant --poste=PC4 --port=${PORT}"
if [[ -n "${IHM_SERVEUR:-}" ]]; then ARGS+=" --serveur=${IHM_SERVEUR}"; fi
echo "  PC4 apprenant - detection automatique du serveur local."
exec bash "$MVN" -B javafx:run "-Djavafx.args=${ARGS}"
