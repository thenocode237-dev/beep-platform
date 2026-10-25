#!/usr/bin/env bash
# Bout en bout sur la stack de développement en cours d'exécution (make up).
set -euo pipefail
cd "$(dirname "$0")/.."
DEV="docker compose -f docker-compose.yml -f docker-compose.dev.yml"

provider="$($DEV exec -T core printenv PAYMENT_PROVIDER | tr -d '\r')"
[ "$provider" = sandbox ] || { echo "La recette de phase 2 exige PAYMENT_PROVIDER=sandbox (aucun paiement réel)." >&2; exit 2; }
export E2E_BILLING_SANDBOX=1

# Compte staff dédié à ce passage, avec un secret TOTP connu du test.
export E2E_STAFF_EMAIL="e2e-$(date +%s)@beep.localhost"
export E2E_STAFF_TOTP_SECRET="JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"
$DEV run --rm -e SPRING_PROFILES_ACTIVE=dev,seed -e BO_SEED_EMAIL="$E2E_STAFF_EMAIL" -e BO_SEED_TOTP_SECRET="$E2E_STAFF_TOTP_SECRET" backoffice-api >/dev/null

cleanup_staff() {
  printf "UPDATE staff_users SET active=false WHERE email='%s'; DELETE FROM staff_sessions WHERE staff_id IN (SELECT id FROM staff_users WHERE email='%s');\n" "$E2E_STAFF_EMAIL" "$E2E_STAFF_EMAIL" \
    | $DEV exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d backoffice' >/dev/null
}
trap cleanup_staff EXIT

node scripts/e2e.mjs
