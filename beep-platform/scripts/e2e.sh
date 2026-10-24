#!/usr/bin/env bash
# Bout en bout sur la stack de développement en cours d'exécution (make up).
set -euo pipefail
cd "$(dirname "$0")/.."
DEV="docker compose -f docker-compose.yml -f docker-compose.dev.yml"

# Compte staff dédié à ce passage, avec un secret TOTP connu du test.
export E2E_STAFF_EMAIL="e2e-$(date +%s)@beep.localhost"
export E2E_STAFF_TOTP_SECRET="JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"
$DEV run --rm -e SPRING_PROFILES_ACTIVE=dev,seed -e BO_SEED_EMAIL="$E2E_STAFF_EMAIL" -e BO_SEED_TOTP_SECRET="$E2E_STAFF_TOTP_SECRET" backoffice-api >/dev/null

node scripts/e2e.mjs
