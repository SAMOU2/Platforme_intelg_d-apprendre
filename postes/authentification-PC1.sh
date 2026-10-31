#!/usr/bin/env bash
# Service HTTPS sur PC1 uniquement. Aucun Docker requis sur les clients.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
for variable in IHM_DB_URL IHM_DB_USER IHM_DB_PASSWORD IHM_TLS_KEYSTORE IHM_TLS_PASSWORD IHM_SMTP_HOST IHM_SMTP_FROM; do
    if [[ -z "${!variable:-}" ]]; then
        echo "[ERREUR] La variable $variable est obligatoire sur PC1." >&2
        exit 1
    fi
done
if ! command -v javac >/dev/null || [[ "$(javac -version 2>&1)" != javac\ 25* ]]; then
    echo "[ERREUR] Installer un JDK 25 et configurer JAVA_HOME sur PC1." >&2
    exit 1
fi
bash ./mvnw -B -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/authentification-classpath.txt
exec java -cp "target/classes:$(cat target/authentification-classpath.txt)" fr.ihm2026.api.service.ServeurAuthentificationHttps
