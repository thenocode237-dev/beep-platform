#!/usr/bin/env bash
# Test de charge de la redirection sur la stack en cours d'exécution (make up && make seed : crée le lien /demo).
# make test-load RATE=1000 DURATION=60s   — le critère de sortie de la phase 1 (1 000 req/s, p95 < 50 ms) se mesure
# sur l'hôte de préproduction ; en CI on vérifie le budget à un débit réduit.
set -euo pipefail
cd "$(dirname "$0")/.."
RATE="${RATE:-200}"
DURATION="${DURATION:-30s}"
echo "== k6 : ${RATE} req/s pendant ${DURATION} sur /${SLUG:-demo}"
# Réseau `edge` : le générateur atteint Caddy comme un visiteur (sans passer par le port publié de l'hôte).
MSYS_NO_PATHCONV=1 docker run --rm --network beep_edge -v "$(pwd)/scripts/load:/scripts:ro" \
  -e RATE="$RATE" -e DURATION="$DURATION" -e TARGET="${TARGET:-http://proxy}" -e HOST="${HOST:-beep.localhost}" -e SLUG="${SLUG:-demo}" \
  grafana/k6:0.54.0 run --summary-trend-stats "avg,med,p(95),p(99),max" /scripts/redirect.js
