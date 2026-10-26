FROM postgres:16-alpine
RUN apk add --no-cache pgbouncer
COPY entrypoint.sh /usr/local/bin/beep-pgbouncer
RUN chmod +x /usr/local/bin/beep-pgbouncer && mkdir -p /etc/pgbouncer && chown -R postgres:postgres /etc/pgbouncer
USER postgres
ENTRYPOINT ["/usr/local/bin/beep-pgbouncer"]
