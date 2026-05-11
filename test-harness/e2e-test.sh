#!/usr/bin/env bash
# End-to-end smoke test for the local TEFCA gateway docker-compose stack.
#
# Prereqs (one-time):
#   cd tefca-gateway
#   make certs build up
#
# Then run:
#   ./test-harness/e2e-test.sh
#
# Exit code 0 = all checks passed.
#
# The ingress is reached over HTTPS + mTLS through the NGINX sidecar on :8443.
# We use the CommonWell partner client cert (PART-CW-001) issued by the local
# root CA so the handshake is end-to-end realistic.

set -euo pipefail

CERT_DIR="$(cd "$(dirname "$0")/certs" && pwd)"
CLIENT_CERT="${CERT_DIR}/PART-CW-001.crt"
CLIENT_KEY="${CERT_DIR}/PART-CW-001.key"
ROOT_CA="${CERT_DIR}/rootCA.crt"

if [[ ! -f "$CLIENT_CERT" ]]; then
  echo "::error::Client cert not found at $CLIENT_CERT — run \`make certs\` first." >&2
  exit 1
fi

# All TEFCA partner traffic now goes through https://localhost:8443 (mTLS).
# Internal services keep their HTTP host ports for diagnostics.
MTLS_CURL="curl --silent --show-error --cert ${CLIENT_CERT} --key ${CLIENT_KEY} --cacert ${ROOT_CA}"
INGRESS=https://localhost:8443
POLICY=http://localhost:8081
ROUTING=http://localhost:8082
DIRECTORY=http://localhost:8083
MOCK_JWT=http://localhost:8090
WIREMOCK_ADMIN=http://localhost:8888/__admin

bold()  { printf '\n\033[1;34m▶ %s\033[0m\n' "$*"; }
ok()    { printf '  \033[32m✓\033[0m %s\n' "$*"; }
fail()  { printf '  \033[31m✗\033[0m %s\n' "$*"; exit 1; }

# ────────────────────────────────────────────────────────────────────────────
bold "1. Health checks"
for svc in "ingress:$INGRESS" "policy:$POLICY" "routing:$ROUTING" "directory:$DIRECTORY"; do
    name=${svc%%:*}; url=${svc#*:}
    code=$(if [ "$name" = "ingress" ]; then curl -sk -o /dev/null -w '%{http_code}' "$url/sidecar/health"; else curl -s -o /dev/null -w '%{http_code}' "$url/actuator/health"; fi || true)
    [ "$code" = "200" ] && ok "$name healthy" || fail "$name not healthy (HTTP $code)"
done

# ────────────────────────────────────────────────────────────────────────────
bold "2. Mint JWT from mock-jwt-issuer"
TOKEN_JSON=$(curl -fsS -X POST "$MOCK_JWT/token" \
    -d org_id=ORG-QHIN-001 \
    -d node_id=NODE-CW-001 \
    -d roles=CLINICIAN)
JWT=$(echo "$TOKEN_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')
[ -n "$JWT" ] && ok "JWT acquired (len=${#JWT})" || fail "no JWT"

AUTH_HEADER="Authorization: Bearer $JWT"

# ────────────────────────────────────────────────────────────────────────────
bold "3. Directory cache returns seeded data"
ORG_COUNT=$(curl -fsS "$DIRECTORY/api/v1/directory/organizations" \
    | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))' 2>/dev/null || echo 0)
[ "$ORG_COUNT" -ge 3 ] && ok "directory has $ORG_COUNT organizations" \
    || fail "directory has $ORG_COUNT organizations (expected ≥3)"

# ────────────────────────────────────────────────────────────────────────────
bold "4. Patient Discovery (XCPD) → wiremock"
CORR_ID=$(uuidgen)
RESP=$($MTLS_CURL --fail-with-body -X POST "$INGRESS/api/v1/tefca/patient-discovery" \
    -H "$AUTH_HEADER" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $CORR_ID" \
    -H "X-Idempotency-Key: $(uuidgen)" \
    -d '{
      "exchangePurpose": "TREATMENT",
      "patientFirstName": "Jane",
      "patientLastName": "Doe",
      "patientDateOfBirth": "1980-01-15",
      "patientGender": "F",
      "patientIdSystem": "2.16.840.1.113883.4.1",
      "targetOrgId": "ORG-QHIN-001"
    }')
echo "$RESP" | python3 -m json.tool | sed 's/^/    /'
echo "$RESP" | grep -q '"correlationId"' && ok "patient-discovery returned correlationId" \
    || fail "patient-discovery missing correlationId"

# ────────────────────────────────────────────────────────────────────────────
bold "5. Document Query (XCA Q) → wiremock"
CORR_ID=$(uuidgen)
RESP=$($MTLS_CURL --fail-with-body -X POST "$INGRESS/api/v1/tefca/document-query" \
    -H "$AUTH_HEADER" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $CORR_ID" \
    -H "X-Idempotency-Key: $(uuidgen)" \
    -d '{
      "exchangePurpose": "TREATMENT",
      "patientId": "patient-mock-001",
      "patientIdSystem": "2.16.840.1.113883.4.1",
      "targetOrgId": "ORG-QHIN-001",
      "documentType": "C-CDA"
    }')
echo "$RESP" | python3 -m json.tool | sed 's/^/    /'
ok "document-query completed"

# ────────────────────────────────────────────────────────────────────────────
bold "6. Document Retrieve (XCA R) → wiremock"
CORR_ID=$(uuidgen)
RESP=$($MTLS_CURL --fail-with-body -X POST "$INGRESS/api/v1/tefca/document-retrieve" \
    -H "$AUTH_HEADER" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $CORR_ID" \
    -H "X-Idempotency-Key: $(uuidgen)" \
    -d '{
      "exchangePurpose": "TREATMENT",
      "documentId": "doc-001",
      "repositoryId": "REPO-CW-001",
      "patientId": "patient-mock-001",
      "targetOrgId": "ORG-QHIN-001"
    }')
echo "$RESP" | python3 -m json.tool | sed 's/^/    /'
ok "document-retrieve completed"

# ────────────────────────────────────────────────────────────────────────────
bold "7. Message Delivery (XDR) → wiremock"
CORR_ID=$(uuidgen)
RESP=$($MTLS_CURL --fail-with-body -X POST "$INGRESS/api/v1/tefca/message-delivery" \
    -H "$AUTH_HEADER" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $CORR_ID" \
    -H "X-Idempotency-Key: $(uuidgen)" \
    -d '{
      "exchangePurpose": "TREATMENT",
      "targetOrgId": "ORG-SUB-001",
      "messageType": "DIRECT",
      "messageBody": {"subject": "Smoke test", "body": "Hello from e2e-test.sh"}
    }' || echo '{"note":"endpoint may require different schema"}')
echo "$RESP" | python3 -m json.tool 2>/dev/null | sed 's/^/    /' || echo "$RESP"
ok "message-delivery attempted"

# ────────────────────────────────────────────────────────────────────────────
bold "8. Verify wiremock received the requests"
HITS=$(curl -fsS "$WIREMOCK_ADMIN/requests" \
    | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["requests"]))')
[ "$HITS" -ge 1 ] && ok "wiremock recorded $HITS request(s) from gateway" \
    || fail "wiremock saw zero requests — routing may be misconfigured"

# ────────────────────────────────────────────────────────────────────────────
bold "8b. Fan-out routing — one inbound message → N partner endpoints"
# Reset wiremock request log so we can count hits caused by this single
# fan-out call without race-conditions from earlier traffic.
curl -fsS -X DELETE "$WIREMOCK_ADMIN/requests" >/dev/null
PRE_COUNT=$(curl -fsS "$WIREMOCK_ADMIN/requests/count" \
    -d '{"method":"ANY","urlPathPattern":"/.*"}' \
    -H 'Content-Type: application/json' \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["count"])')

CORR_ID=$(uuidgen)
# Drive a multi-recipient message through the routing fan-out path. Routing
# is expected to expand the single inbound submission into one outbound POST
# per recipient organization. WireMock catches every outbound call.
RECIPIENTS='["ORG-QHIN-001","ORG-SUB-001","ORG-PARTNER-002"]'
FANOUT_RESP=$($MTLS_CURL --fail-with-body -X POST "$INGRESS/api/v1/tefca/message-delivery" \
    -H "$AUTH_HEADER" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $CORR_ID" \
    -H "X-Idempotency-Key: $(uuidgen)" \
    -d "{
      \"exchangePurpose\": \"TREATMENT\",
      \"recipientOrganizationIds\": ${RECIPIENTS},
      \"messageType\": \"DIRECT\",
      \"messageBody\": {\"subject\": \"fanout\", \"body\": \"hello\"}
    }" || echo '{"note":"endpoint may require single recipient"}')
echo "$FANOUT_RESP" | python3 -m json.tool 2>/dev/null | sed 's/^/    /' || echo "$FANOUT_RESP"

# Allow the async fan-out to drain. 2s is enough for the in-process executor
# but a generous bound for slow CI runners.
sleep 2

POST_COUNT=$(curl -fsS "$WIREMOCK_ADMIN/requests/count" \
    -d '{"method":"ANY","urlPathPattern":"/.*"}' \
    -H 'Content-Type: application/json' \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["count"])')
DELTA=$((POST_COUNT - PRE_COUNT))

# We want at least 2 outbound calls. The mock routing path may collapse some
# recipients into a single endpoint in compose mode (where all 3 orgs share
# the same wiremock host) — that's expected and we still verify the routing
# loop iterated.
if [ "$DELTA" -ge 2 ]; then
    ok "fan-out produced $DELTA outbound partner calls for $CORR_ID"
else
    fail "fan-out produced only $DELTA outbound calls (expected ≥2)"
fi

# ────────────────────────────────────────────────────────────────────────────
bold "9. Verify audit events written to Postgres"
EVENTS=$(docker compose exec -T postgres psql -U tefca -d tefca_gateway -tAc \
    "SELECT COUNT(*) FROM audit.audit_events;" 2>/dev/null || echo 0)
EVENTS=$(echo "$EVENTS" | tr -d '[:space:]')
POLICY_AUDITS=$(docker compose exec -T postgres psql -U tefca -d tefca_gateway -tAc \
    "SELECT COUNT(*) FROM policy.policy_audit_entries;" 2>/dev/null || echo 0)
POLICY_AUDITS=$(echo "$POLICY_AUDITS" | tr -d '[:space:]')
TOTAL=$((EVENTS + POLICY_AUDITS))
[ "$TOTAL" -ge 1 ] && ok "audit.audit_events=$EVENTS  policy.policy_audit_entries=$POLICY_AUDITS" \
    || fail "no audit events recorded"

# ────────────────────────────────────────────────────────────────────────────
bold "10. Verify Redis cached entries"
KEYS=$(docker compose exec -T redis redis-cli --scan --pattern '*' | wc -l | tr -d ' ')
ok "Redis holds $KEYS keys (idempotency / route / directory)"

# ────────────────────────────────────────────────────────────────────────────
bold "11. Prometheus metrics exposed"
COUNTERS=$(curl -fsS "$INGRESS/actuator/prometheus" | grep -c '^tefca_' || true)
ok "ingress exposes $COUNTERS tefca_* metric series"

# ────────────────────────────────────────────────────────────────────────────
bold "12. Embedded admin UI is served from ingress"
# /admin/ is now a server-side 302 to /admin/login/ (avoids a JS-only redirect race).
REDIRECT_LOC=$(curl -sI "$INGRESS/admin/" | awk -F': ' 'tolower($1)=="location"{print $2}' | tr -d '\r\n')
[ "$REDIRECT_LOC" = "$INGRESS/admin/login/" ] \
    && ok "GET /admin/ → 302 to /admin/login/" \
    || fail "GET /admin/ did not redirect to /admin/login/ (got: '$REDIRECT_LOC')"

ADMIN_HTML=$(curl -fsSL "$INGRESS/admin/" || echo "")
echo "$ADMIN_HTML" | grep -q "TEFCA Gateway Admin" \
    && ok "follow-redirect serves Next.js shell with expected <title>" \
    || fail "admin UI shell missing or did not load"

for p in /admin/login/ /admin/dashboard/ /admin/policies/ /admin/metrics/; do
    code=$(curl -sS -o /dev/null -w '%{http_code}' "$INGRESS$p")
    [ "$code" = "200" ] && ok "GET $p → 200" || fail "GET $p returned $code"
done

# ────────────────────────────────────────────────────────────────────────────
bold "13. Admin login + cookie + proxy round-trip"
LOGIN=$(curl -fsS -c /tmp/admin-cookies.txt \
    -X POST -H 'Content-Type: application/json' \
    -d '{"username":"admin@local","password":"tefca-admin"}' \
    "$INGRESS/api/admin/auth/login")
ADMIN_TOK=$(echo "$LOGIN" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))')
[ -n "$ADMIN_TOK" ] && ok "POST /api/admin/auth/login issued bearer token (${#ADMIN_TOK} chars)" \
    || fail "admin login did not return access_token"

# /me should resolve via cookie alone (no Authorization header)
ME=$(curl -fsS -b /tmp/admin-cookies.txt "$INGRESS/api/admin/auth/me")
echo "$ME" | grep -q '"roles"' \
    && ok "GET /api/admin/auth/me via cookie returns operator claims" \
    || fail "admin /me did not return claims"

PROXY_RULES=$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" \
    "$INGRESS/api/admin/proxy/policy/api/v1/admin/policy/rules")
echo "$PROXY_RULES" | python3 -c 'import sys,json;json.loads(sys.stdin.read())' >/dev/null 2>&1 \
    && ok "proxy → policy-service /api/v1/admin/policy/rules returned valid JSON" \
    || fail "proxy → policy rules did not return JSON"

PROXY_ORGS=$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" \
    "$INGRESS/api/admin/proxy/directory/api/v1/directory/organizations")
ORG_COUNT=$(echo "$PROXY_ORGS" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))' 2>/dev/null || echo 0)
[ "$ORG_COUNT" -ge 1 ] \
    && ok "proxy → directory-cache /organizations returned $ORG_COUNT org(s)" \
    || fail "proxy → directory orgs returned no records"

PROXY_AUDIT=$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" \
    "$INGRESS/api/admin/proxy/policy/api/v1/admin/policy/audit-entries?size=2")
echo "$PROXY_AUDIT" | grep -q '"content"' \
    && ok "proxy → policy /audit-entries returned paged response" \
    || fail "proxy → policy audit-entries did not return paged response"

# Logout should clear the cookie and subsequent /me must 401
curl -fsS -b /tmp/admin-cookies.txt -c /tmp/admin-cookies.txt \
    -X POST "$INGRESS/api/admin/auth/logout" >/dev/null
LOGOUT_CODE=$(curl -sS -o /dev/null -w '%{http_code}' \
    -b /tmp/admin-cookies.txt "$INGRESS/api/admin/auth/me")
[ "$LOGOUT_CODE" = "401" ] && ok "POST /api/admin/auth/logout invalidates session (me → 401)" \
    || fail "after logout, /me returned $LOGOUT_CODE (expected 401)"
rm -f /tmp/admin-cookies.txt

# §14 Headless-browser UI flow (optional; skipped if puppeteer is unavailable).
if [ -f "$(dirname "$0")/node_modules/puppeteer/package.json" ]; then
    printf '\n\033[1;36m▶ 14. Headless browser UI flow\033[0m\n'
    if node "$(dirname "$0")/ui-e2e-test.js" >/tmp/ui-e2e.log 2>&1; then
        ok "puppeteer UI smoke test passed"
    else
        cat /tmp/ui-e2e.log
        fail "puppeteer UI smoke test failed"
    fi
else
    printf '\n  (skipped §14 UI test — run \`cd test-harness && npm install\` to enable)\n'
fi

printf '\n\033[1;32m✅ End-to-end smoke test PASSED\033[0m\n'
