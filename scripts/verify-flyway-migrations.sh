#!/usr/bin/env bash
# Fail fast if the canonical Flyway migration directory is missing or empty.
# Also enforce that compose-only SQL is kept out of prod Flyway locations.
# Used by GitHub Actions (CI + deploy) and runnable locally before docker compose.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MIG="${ROOT}/services/tefca-gateway-app/src/main/resources/db/migration"
COMPOSE_MIG="${ROOT}/services/tefca-gateway-app/src/main/resources/db/compose-migration"

if [[ ! -d "$MIG" ]]; then
  echo "ERROR: Flyway migration directory missing: $MIG" >&2
  exit 1
fi

v_count="$(find "$MIG" -maxdepth 1 -type f -name 'V*.sql' 2>/dev/null | wc -l | tr -d ' ')"
if [[ "${v_count}" -lt 1 ]]; then
  echo "ERROR: No versioned migrations (V*.sql) under ${MIG}" >&2
  exit 1
fi

r_count="$(find "$MIG" -maxdepth 1 -type f -name 'R*.sql' 2>/dev/null | wc -l | tr -d ' ' || true)"

if [[ ! -d "$COMPOSE_MIG" ]]; then
  echo "ERROR: Compose migration directory missing: $COMPOSE_MIG" >&2
  exit 1
fi

if find "$MIG" -maxdepth 1 -type f -name 'R002__compose_endpoint_urls.sql' | grep -q .; then
  echo "ERROR: compose-only migration R002__compose_endpoint_urls.sql must not live under prod Flyway path (${MIG})" >&2
  exit 1
fi

if find "$MIG" -maxdepth 1 -type f -name '*.sql' -exec grep -nH 'wiremock' {} + | grep -q .; then
  echo "ERROR: prod Flyway migrations must not reference compose host 'wiremock' (${MIG})" >&2
  exit 1
fi

compose_r_count="$(find "$COMPOSE_MIG" -maxdepth 1 -type f -name 'R*.sql' 2>/dev/null | wc -l | tr -d ' ' || true)"
echo "OK: Flyway migrations present — ${v_count} versioned (V*.sql), ${r_count} repeatable (R*.sql) in ${MIG}; compose-only repeatables: ${compose_r_count} in ${COMPOSE_MIG}"
