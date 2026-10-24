#!/usr/bin/env bash
# Crée .env depuis .env.example puis remplit les secrets vides ou « change-me » avec des valeurs aléatoires.
# Idempotent : ne touche jamais un secret déjà défini.
set -euo pipefail
env_file="${1:-.env}"

[ -f "$env_file" ] || { cp .env.example "$env_file"; echo "créé : $env_file"; }

rand_b64() { node -e "process.stdout.write(require('crypto').randomBytes(32).toString('base64'))"; }
rand_hex() { node -e "process.stdout.write(require('crypto').randomBytes(24).toString('hex'))"; }

fill() { # fill VAR generator
  local var="$1" gen="$2" current
  current="$(grep -E "^${var}=" "$env_file" | head -1 | cut -d= -f2- | sed 's/[[:space:]]*#.*$//')"
  if [ -z "$current" ] || [ "$current" = "change-me" ]; then
    local value; value="$($gen)"
    # remplace la ligne en conservant le commentaire de fin de ligne
    sed -i.bak -E "s|^${var}=[^#]*|${var}=${value}  |" "$env_file" && rm -f "$env_file.bak"
    echo "secret généré : $var"
  fi
}

for v in PG_SUPERUSER_PASSWORD BEEP_DB_PASSWORD BO_DB_PASSWORD ADMIN_API_TOKEN; do fill "$v" rand_hex; done
for v in TOTP_ENCRYPTION_KEY BO_ENCRYPTION_KEY; do fill "$v" rand_b64; done
