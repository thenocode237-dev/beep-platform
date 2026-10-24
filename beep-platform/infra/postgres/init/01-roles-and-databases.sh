#!/bin/sh
# Exécuté une seule fois, à l'initialisation du volume pg_data.
# Deux bases, deux rôles applicatifs SANS aucun droit croisé : beep_app ne voit pas `backoffice`, et inversement.
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
     -v beep_pwd="$BEEP_DB_PASSWORD" -v bo_pwd="$BO_DB_PASSWORD" <<'SQL'
CREATE ROLE beep_app LOGIN PASSWORD :'beep_pwd';
CREATE ROLE bo_app   LOGIN PASSWORD :'bo_pwd';

CREATE DATABASE beep       OWNER beep_app;
CREATE DATABASE backoffice OWNER bo_app;

REVOKE ALL ON DATABASE beep       FROM PUBLIC;
REVOKE ALL ON DATABASE backoffice FROM PUBLIC;
GRANT CONNECT ON DATABASE beep       TO beep_app;
GRANT CONNECT ON DATABASE backoffice TO bo_app;
SQL
