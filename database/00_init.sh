#!/bin/bash
# Postgres init script — runs after the cluster is created.
# Mimics Flyway ordering: versioned migrations (V*.sql) first, then repeatable
# seeds (R*.sql). Both types live in the same migrations directory mount.
set -e

run_versioned() {
    local dir="$1"
    if [ ! -d "$dir" ]; then return 0; fi
    for f in $(ls "$dir"/V*.sql 2>/dev/null | sort); do
        echo "[init-db] applying $f"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$f"
    done
}

run_repeatable() {
    local dir="$1"
    if [ ! -d "$dir" ]; then return 0; fi
    for f in $(ls "$dir"/R*.sql 2>/dev/null | sort); do
        echo "[init-db] applying $f"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$f"
    done
}

# Primary migrations (versioned schema first, then repeatable seeds)
run_versioned /docker-entrypoint-initdb.d/migrations
run_repeatable /docker-entrypoint-initdb.d/migrations

# Compose-specific overlays (optional, may be empty)
run_versioned /docker-entrypoint-initdb.d/compose-migrations
run_repeatable /docker-entrypoint-initdb.d/compose-migrations

echo "[init-db] complete"
