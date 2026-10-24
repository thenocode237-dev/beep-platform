#!/usr/bin/env bash
# Rejoue toutes les migrations Flyway (core + back office) sur une base vierge, vérifie leur intégrité et leur idempotence,
# et contrôle la politique expand/contract. PostgreSQL réel, jetable.
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/lib-pg.sh
start_temp_pg
for module in beep/core backoffice/api; do
  echo "== migrations : $module"
  mvn_in "$module" verify -Pintegration -pl adapter-out-postgres -am \
    -Dtest='MigrationPolicyTest' -Dit.test='MigrationIT' -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
done
