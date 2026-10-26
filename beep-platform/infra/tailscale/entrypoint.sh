#!/bin/sh
set -eu
if [ "${1:-}" = "status" ]; then
  printf '{"origin":"%s","status":' "$BO_PUBLIC_URL"
  tailscale status --json
  printf ',"serve":'
  tailscale serve status --json
  printf '}\n'
  exit 0
fi
# containerboot reads file: credentials directly: the key is never a process
# argument or an environment value. Do not enable shell tracing here.
secret=/run/secrets/tailscale_authkey
if [ ! -s "$secret" ]; then
  echo "Tailscale: fichier secret d'inscription manquant ou vide." >&2
  exit 1
fi
export TS_AUTHKEY="file:$secret"
exec /usr/local/bin/containerboot
