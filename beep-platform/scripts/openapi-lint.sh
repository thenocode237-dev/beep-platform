#!/usr/bin/env bash
# Valide les contrats OpenAPI (syntaxe, références, schémas) avec Redocly.
set -euo pipefail
cd "$(dirname "$0")/.."
for spec in contracts/*.yaml; do
  echo "== $spec"
  npx --yes @redocly/cli@1.34.5 lint "$spec" --skip-rule=no-empty-servers --skip-rule=operation-4xx-response --skip-rule=security-defined --skip-rule=info-license --skip-rule=no-ambiguous-paths
done
