#!/usr/bin/env bash
# Restauration.
#   dev  : ./scripts/restore.sh backups/20260101T100000Z        (préfixe commun aux deux dumps)
#   prod : ./scripts/restore.sh 2026-01-01T10:00:00Z             (PITR WAL-G ; voir docs/runbook-backup.md)
set -euo pipefail
cd "$(dirname "$0")/.."
from="${1:-}"
[ -n "$from" ] || { echo "usage : restore.sh <préfixe de dump | horodatage ISO pour le PITR>" >&2; exit 2; }

if [[ "$from" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T ]]; then
  echo "PITR vers $from : procédure en production, voir docs/runbook-backup.md (arrêt de la stack, wal-g backup-fetch, recovery_target_time)." >&2
  exit 3
fi

DEV="docker compose -f docker-compose.yml -f docker-compose.dev.yml"
for db in beep backoffice; do
  file="${from}-${db}.dump"
  [ -f "$file" ] || { echo "introuvable : $file" >&2; exit 1; }
done

echo "Arrêt des applications (la base reste démarrée)…"
$DEV stop core worker backoffice-api >/dev/null
for db in beep backoffice; do
  file="${from}-${db}.dump"
  echo "Restauration de $db depuis $file"
  # --clean --if-exists : remplace les objets existants ; --no-owner : les rôles applicatifs restent propriétaires
  $DEV exec -T postgres sh -c "pg_restore -U \"\$POSTGRES_USER\" -d $db --clean --if-exists --no-owner" < "$file"
done
$DEV start core worker backoffice-api >/dev/null
echo "Restauration terminée."
