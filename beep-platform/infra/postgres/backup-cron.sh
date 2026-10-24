#!/bin/sh
# Sauvegarde de base quotidienne (03:00 UTC) + rétention. Les WAL sont archivés en continu par PostgreSQL lui-même.
set -eu
retain="${BACKUP_RETAIN_FULL:-7}"

run_backup() {
  echo "$(date -u +%FT%TZ) wal-g backup-push"
  wal-g backup-push "$PGDATA" && wal-g delete retain FULL "$retain" --confirm
}

# Première sauvegarde au démarrage si le dépôt est vide, puis une fois par jour.
wal-g backup-list 2>/dev/null | grep -q base_ || run_backup
while true; do
  now=$(date -u +%s)
  next=$(date -u -d "tomorrow 03:00" +%s)
  sleep $((next - now))
  run_backup
done
