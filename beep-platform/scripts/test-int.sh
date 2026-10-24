#!/usr/bin/env bash
# Tests d'intégration des adapters PostgreSQL (core + back office) sur un PostgreSQL 16 réel et jetable.
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/lib-pg.sh
start_temp_pg
# On ne rejoue pas les tests unitaires (déjà couverts par `make test-unit`) : seuls les *IT tournent.
mvn_in beep/core verify -Pintegration -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
mvn_in backoffice/api verify -Pintegration -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
