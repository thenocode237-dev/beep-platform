#!/usr/bin/env bash
# Tests de contrat core <-> back office :
#   1. le contrat OpenAPI est valide ;
#   2. le core RÉEL (stack en cours d'exécution : `make up`) respecte ses schémas, sa sécurité et sa version.
# Le client du back office est vérifié de bout en bout par `make test-e2e` (la page « Utilisateurs » lit via le contrat).
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/openapi-lint.sh

[ -f .env ] || { echo "créez .env (make init) et démarrez la stack (make up)" >&2; exit 1; }
token="$(grep -E '^ADMIN_API_TOKEN=' .env | cut -d= -f2- | sed 's/[[:space:]]*#.*$//; s/[[:space:]]*$//')"

# Le réseau `backend` est interne : on s'y attache pour joindre le core comme le fait le back office.
MSYS_NO_PATHCONV=1 docker run --rm --network beep_backend \
  -e CORE_URL=http://core:8080 -e ADMIN_API_TOKEN="$token" \
  -v "$(pwd):/w" -w /w node:22-alpine node scripts/contract-check.mjs
