#!/bin/sh
set -eu
export PGPASSWORD="$BEEP_DB_PASSWORD"
psql -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 6432 -U beep_app -d beep -Atc 'SELECT current_database(), current_user, 1'
exit 0
