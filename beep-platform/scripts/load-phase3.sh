#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${COLLECT_KEYS:?Provide public keys for dedicated load-test workspaces, never production accounts}"
MSYS_NO_PATHCONV=1 docker run --rm --network beep_edge -v "$(pwd)/scripts/load:/scripts:ro" \
  -e COLLECT_KEYS -e COLLECT_ORIGIN="${COLLECT_ORIGIN:-https://load.example}" \
  -e REDIRECT_RATE="${REDIRECT_RATE:-5000}" -e EVENT_RATE="${EVENT_RATE:-2000}" -e DURATION="${DURATION:-60s}" \
  -e TARGET="${TARGET:-http://proxy}" -e HOST="${HOST:-beep.localhost}" -e SLUG="${SLUG:-demo}" \
  grafana/k6:0.54.0 run /scripts/phase3.js
