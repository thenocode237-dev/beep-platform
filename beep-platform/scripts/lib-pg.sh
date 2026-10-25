#!/usr/bin/env bash
# Utilitaires : PostgreSQL jetable pour les tests d'intégration (sans dépendre de Testcontainers ni d'un JDK local).
NET="beep-it-$$"
PGC="beep-it-pg-$$"

stop_temp_pg() {
  docker rm -f "$PGC" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}

start_temp_pg() {
  trap stop_temp_pg EXIT
  docker network create "$NET" >/dev/null
  docker run -d --name "$PGC" --network "$NET" -e POSTGRES_PASSWORD=it postgres:16-alpine >/dev/null
  # La connexion TCP n'aboutit que sur le serveur définitif (le serveur d'initialisation n'écoute que sur le socket Unix).
  for _ in $(seq 1 60); do
    docker exec "$PGC" psql -h 127.0.0.1 -U postgres -tAc 'select 1' >/dev/null 2>&1 && break
    sleep 1
  done
  export TEST_DATABASE_URL="jdbc:postgresql://${PGC}:5432/postgres?user=postgres&password=it"
}

# mvn_in <répertoire> <arguments maven…> : Maven JDK 21 dans le réseau du PostgreSQL jetable
mvn_in() {
  local dir="$1"; shift
  MSYS_NO_PATHCONV=1 docker run --rm --network "$NET" -e TEST_DATABASE_URL \
    -v "$(pwd)/${dir}:/src" -v "$(pwd)/contracts:/contracts:ro" -v beep-m2:/root/.m2 -w /src "${MVN_IMAGE:-maven:3.9-eclipse-temurin-21}" mvn -B -ntp "$@"
}
