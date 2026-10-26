#!/bin/sh
set -eu
umask 077
: "${BEEP_DB_PASSWORD:?required}"
# No shell tracing: credentials exist only in this private runtime file.
escaped=$(printf '%s' "$BEEP_DB_PASSWORD" | sed 's/\\/\\\\/g; s/"/""/g')
printf '"beep_app" "%s"\n' "$escaped" > /etc/pgbouncer/users.txt
cat > /etc/pgbouncer/pgbouncer.ini <<'INI'
[databases]
beep = host=postgres port=5432 dbname=beep
[pgbouncer]
listen_addr = 0.0.0.0
listen_port = 6432
auth_type = scram-sha-256
auth_file = /etc/pgbouncer/users.txt
pool_mode = transaction
max_client_conn = 500
default_pool_size = 30
reserve_pool_size = 5
server_reset_query = DISCARD ALL
ignore_startup_parameters = extra_float_digits
pidfile = /tmp/pgbouncer.pid
log_connections = 0
log_disconnections = 0
INI
exec pgbouncer /etc/pgbouncer/pgbouncer.ini
