#!/usr/bin/env bash
# =====================================================================
#  POSTE 3 (PC3) : ENSEIGNANT
#  Equivalence Linux/macOS de postes/3-enseignant-PC3.cmd (la salle est sous Windows ; ce
#  fichier sert aux tests et aux postes de developpement).
#  Le serveur est decouvert automatiquement ; le PIN individuel est saisi a l'inscription.
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
MVN=./mvnw
PORT="${PORT:-7777}"
ARGS="--role=enseignant --poste=PC3 --port=${PORT}"
if [[ -n "${IHM_SERVEUR:-}" ]]; then ARGS+=" --serveur=${IHM_SERVEUR}"; fi
echo "  PC3 enseignant - detection automatique du serveur local."
exec bash "$MVN" -B javafx:run "-Djavafx.args=${ARGS}"
