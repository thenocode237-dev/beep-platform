#!/usr/bin/env bash
# Dump logique des deux bases (développement) -> ./backups/<horodatage>-<base>.dump (format custom pg_dump).
# En production, la sauvegarde est continue (WAL-G) : voir docker-compose.prod.yml et docs/runbook-backup.md.
set -euo pipefail
cd "$(dirname "$0")/.."
DEV="docker compose -f docker-compose.yml -f docker-compose.dev.yml"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p backups
for db in beep backoffice; do
  file="backups/${stamp}-${db}.dump"
  $DEV exec -T postgres sh -c "pg_dump -U \"\$POSTGRES_USER\" -Fc $db" > "$file"
  echo "sauvegardé : $file ($(wc -c < "$file") octets)"
done
