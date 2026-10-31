#!/usr/bin/env bash
# =====================================================================
#  POSTE 1 (PC1) : le SERVEUR de la salle
#  Equivalence Linux/macOS de postes/1-serveur-PC1.cmd (la salle est sous Windows ; ce
#  fichier sert aux tests et aux postes de developpement).
#  Les postes clients decouvrent automatiquement ce serveur sur le reseau local.
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
MVN=./mvnw
PORT="${PORT:-7777}"
echo "  PC1 serveur. Decouverte des clients automatique sur le reseau local."
exec bash "$MVN" -B javafx:run "-Djavafx.args=--role=serveur --poste=PC1 --port=${PORT}"
