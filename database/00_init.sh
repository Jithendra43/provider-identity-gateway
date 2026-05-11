#!/bin/bash
# Postgres init script — runs after the cluster is created.
# Applies all Flyway-style migrations, then seed data, in lexicographic order.
set -e

run_sql_dir() {
    local dir="$1"
    if [ ! -d "$dir" ]; then return 0; fi
    for f in $(ls "$dir"/*.sql 2>/dev/null | sort); do
        echo "[init-db] applying $f"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$f"
    done
}

run_sql_dir /docker-entrypoint-initdb.d/migrations
run_sql_dir /docker-entrypoint-initdb.d/seed
echo "[init-db] complete"
