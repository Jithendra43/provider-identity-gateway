#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# e2e-prod.sh — exercise the production gateway end-to-end
# ──────────────────────────────────────────────────────────────────────────────
# Required env:
#   ADMIN_SESSION   e.g. 'SESSION=NWUz...'  (copied from browser DevTools)
#   PARTNER_JWT     bearer token issued by the configured OIDC issuer.
#                   When unset, TEFCA subcommands auto-mint via
#                   `mint-jwt` against the in-app test IdP
#                   (test/non-prod env only).
# Optional env:
#   GATEWAY_URL     default https://provider-identity-gw.c-hit.ai
#                   (override to https://provider-identity-gw-test.c-hit.ai)
#   ADMIN_URL       default https://provider-identity-gw.c-hit.ai:8444
#                   (override to https://provider-identity-gw-test.c-hit.ai:8444)
#
# Subcommands:
#   onboard <ORG_ID> <Name>          POST  /api/v1/admin/partners
#   list [STATUS]                    GET   /api/v1/admin/partners[?status=]
#   get <PARTNER_ID>                 GET   /api/v1/admin/partners/{id}
#   offboard <PARTNER_ID> [reason]   DELETE /api/v1/admin/partners/{id}
#   mint-jwt <ORG_ID> <NODE_ID> [ROLES=PROVIDER] [SCOPE=tefca.read,tefca.write] [TTL=3600]
#                                    POST  /oauth2/token via admin proxy
#                                    (test env only — controlled by
#                                    tefca.test-idp.token-endpoint-enabled)
#   patient-discovery <ORG_ID>       POST  /api/v1/tefca/patient-discovery
#   document-query    <ORG_ID>       POST  /api/v1/tefca/document-query
#   document-retrieve <ORG_ID>       POST  /api/v1/tefca/document-retrieve
#   message-delivery  <ORG_ID>       POST  /api/v1/tefca/message-delivery
#   policy-rules                     GET   admin proxy → policy rules
#   directory-orgs                   GET   admin proxy → directory orgs
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

GW="${GATEWAY_URL:-https://provider-identity-gw.c-hit.ai}"
ADMIN="${ADMIN_URL:-https://provider-identity-gw.c-hit.ai:8444}"
OUT="$(cd "$(dirname "$0")" && pwd)/out"

c_red()   { printf '\033[31m%s\033[0m\n' "$*"; }
c_grn()   { printf '\033[32m%s\033[0m\n' "$*"; }
c_blue()  { printf '\033[36m▶ %s\033[0m\n' "$*"; }

require_admin() {
  [[ -n "${ADMIN_SESSION:-}" ]] || { c_red "ADMIN_SESSION env var required"; exit 1; }
}

require_cert() {
  local org="$1"
  [[ -f "$OUT/${org}.crt" && -f "$OUT/${org}.key" ]] \
    || { c_red "Missing $OUT/${org}.{crt,key}. Run: ./gen-partner-cert.sh ${org} \"Display\""; exit 1; }
}

require_jwt() {
  if [[ -z "${PARTNER_JWT:-}" ]]; then
    if [[ -n "${AUTO_MINT_ORG:-}" && -n "${AUTO_MINT_NODE:-}" && -n "${ADMIN_SESSION:-}" ]]; then
      c_blue "PARTNER_JWT unset — auto-minting via /oauth2/token (org=${AUTO_MINT_ORG})"
      PARTNER_JWT=$(mint_jwt_quiet "$AUTO_MINT_ORG" "$AUTO_MINT_NODE" "PROVIDER" "tefca.read,tefca.write" 3600)
      [[ -n "$PARTNER_JWT" ]] || { c_red "auto-mint failed"; exit 1; }
      export PARTNER_JWT
    else
      c_red "PARTNER_JWT env var required (or run: ./e2e-prod.sh mint-jwt <ORG_ID> <NODE_ID>)"
      exit 1
    fi
  fi
}

mint_jwt_quiet() {
  local org="$1" node="$2" roles="$3" scope="$4" ttl="$5"
  curl_admin -X POST -G \
    --data-urlencode "client_id=${org}" \
    --data-urlencode "org_id=${org}" \
    --data-urlencode "node_id=${node}" \
    --data-urlencode "roles=${roles}" \
    --data-urlencode "scope=${scope}" \
    --data-urlencode "ttl_seconds=${ttl}" \
    "${ADMIN}/api/admin/proxy/ingress/oauth2/token" \
    | jq -r '.access_token // empty'
}

curl_admin() {
  curl -sS -H "Cookie: ${ADMIN_SESSION}" "$@"
}

# ──────────────────────────────────────────────────────────────────────────────
cmd_onboard() {
  require_admin
  local org="${1:?usage: onboard <ORG_ID> <Name>}"
  local name="${2:?usage: onboard <ORG_ID> <Name>}"
  require_cert "$org"

  c_blue "Onboarding ${org}"
  # Prefer the chained PEM (leaf + E2E Test CA) when present so AWS ALB can
  # validate the chain back to the trust store; fall back to leaf-only.
  local pem_src="$OUT/${org}.pem"
  [[ -f "$pem_src" ]] || pem_src="$OUT/${org}.crt"
  local pem; pem=$(awk 'BEGIN{ORS="\\n"} {print}' "$pem_src")
  local body
  body=$(cat <<JSON
{
  "orgId": "${org}",
  "name": "${name}",
  "environment": "TEST",
  "contactEmail": "ops+${org,,}@c-hit.ai",
  "certificatePem": "${pem}",
  "allowedModalities": ["XCPD","XCA_QUERY","XCA_RETRIEVE","XDR"],
  "allowedScopes": ["tefca.read","tefca.write"],
  "requestsPerMinute": 100
}
JSON
)
  local resp; resp=$(curl_admin -w '\nHTTP %{http_code}\n' \
    -X POST -H 'Content-Type: application/json' \
    -d "$body" \
    "${ADMIN}/api/admin/proxy/ingress/api/v1/admin/partners")
  echo "$resp"
  echo "$resp" | grep -q 'HTTP 201' && c_grn "✔ onboarded" || c_red "✘ onboard failed"
}

cmd_list() {
  require_admin
  local status="${1:-}"
  local q=""; [[ -n "$status" ]] && q="?status=${status}"
  curl_admin "${ADMIN}/api/admin/proxy/ingress/api/v1/admin/partners${q}" | jq .
}

cmd_get() {
  require_admin
  local pid="${1:?usage: get <PARTNER_ID>}"
  curl_admin "${ADMIN}/api/admin/proxy/ingress/api/v1/admin/partners/${pid}" | jq .
}

cmd_offboard() {
  require_admin
  local pid="${1:?usage: offboard <PARTNER_ID> [reason]}"
  local reason="${2:-Offboarded by e2e-prod.sh}"
  c_blue "Offboarding ${pid}"
  curl_admin -w '\nHTTP %{http_code}\n' \
    -X DELETE -H 'Content-Type: application/json' \
    -d "{\"reason\":\"${reason}\"}" \
    "${ADMIN}/api/admin/proxy/ingress/api/v1/admin/partners/${pid}"
}

cmd_mint_jwt() {
  require_admin
  local org="${1:?usage: mint-jwt <ORG_ID> <NODE_ID> [ROLES] [SCOPE] [TTL]}"
  local node="${2:?usage: mint-jwt <ORG_ID> <NODE_ID> [ROLES] [SCOPE] [TTL]}"
  local roles="${3:-PROVIDER}"
  local scope="${4:-tefca.read,tefca.write}"
  local ttl="${5:-3600}"
  c_blue "Minting test JWT (org=${org} node=${node} roles=${roles} ttl=${ttl}s)"
  local resp; resp=$(curl_admin -w '\nHTTP %{http_code}\n' -X POST -G \
    --data-urlencode "client_id=${org}" \
    --data-urlencode "org_id=${org}" \
    --data-urlencode "node_id=${node}" \
    --data-urlencode "roles=${roles}" \
    --data-urlencode "scope=${scope}" \
    --data-urlencode "ttl_seconds=${ttl}" \
    "${ADMIN}/api/admin/proxy/ingress/oauth2/token")
  echo "$resp"
  local jwt; jwt=$(echo "$resp" | sed '/^HTTP /d' | jq -r '.access_token // empty' 2>/dev/null || true)
  if [[ -n "$jwt" ]]; then
    c_grn "✔ minted — export PARTNER_JWT=$jwt"
    echo "export PARTNER_JWT='$jwt'"
  else
    c_red "✘ mint failed (is tefca.test-idp.token-endpoint-enabled=true?)"
  fi
}

# ── TEFCA partner traffic (mTLS + Bearer) ─────────────────────────────────────
tefca_call() {
  local org="$1"; local op="$2"; local body="$3"
  require_cert "$org"
  AUTO_MINT_ORG="${AUTO_MINT_ORG:-$org}"
  AUTO_MINT_NODE="${AUTO_MINT_NODE:-NODE-${org}-001}"
  require_jwt
  local cid; cid="prod-${op}-$(date +%s)-$$"
  c_blue "POST /api/v1/tefca/${op} (correlationId=${cid})"
  curl -sS \
    --cert "$OUT/${org}.crt" --key "$OUT/${org}.key" \
    -w '\nHTTP %{http_code}\n' \
    -H "Authorization: Bearer ${PARTNER_JWT}" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: ${cid}" \
    -H "X-Idempotency-Key: ${cid}-idem" \
    -d "$body" \
    "${GW}/api/v1/tefca/${op}"
}

cmd_patient_discovery() {
  local org="${1:?usage: patient-discovery <ORG_ID>}"
  tefca_call "$org" "patient-discovery" '{
    "exchangePurpose": "TREATMENT",
    "patientFirstName": "Jane",
    "patientLastName": "Doe",
    "patientDateOfBirth": "1980-01-15",
    "patientGender": "F",
    "patientIdSystem": "2.16.840.1.113883.4.1",
    "targetOrgId": "ORG-QHIN-001"
  }'
}

cmd_document_query() {
  local org="${1:?usage: document-query <ORG_ID>}"
  tefca_call "$org" "document-query" '{
    "exchangePurpose": "TREATMENT",
    "patientId": "patient-doe-001",
    "patientIdSystem": "2.16.840.1.113883.4.1",
    "targetOrgId": "ORG-QHIN-001",
    "documentType": "C-CDA",
    "dateFrom": "2024-01-01",
    "dateTo": "2026-12-31"
  }'
}

cmd_document_retrieve() {
  local org="${1:?usage: document-retrieve <ORG_ID>}"
  tefca_call "$org" "document-retrieve" '{
    "exchangePurpose": "TREATMENT",
    "documentId": "doc-001",
    "repositoryId": "REPO-CW-001",
    "patientId": "patient-doe-001",
    "targetOrgId": "ORG-QHIN-001"
  }'
}

cmd_message_delivery() {
  local org="${1:?usage: message-delivery <ORG_ID>}"
  tefca_call "$org" "message-delivery" '{
    "exchangePurpose": "TREATMENT",
    "targetOrgId": "ORG-SUB-001",
    "messageType": "DIRECT",
    "patientId": "patient-doe-001",
    "messageBody": {
      "subject": "Discharge summary — Jane Doe",
      "body": "Patient discharged, follow-up in 7d.",
      "attachments": []
    }
  }'
}

# ── Admin proxy convenience ───────────────────────────────────────────────────
cmd_policy_rules() {
  require_admin
  curl_admin "${ADMIN}/api/admin/proxy/policy/api/v1/admin/policy/rules" | jq .
}
cmd_directory_orgs() {
  require_admin
  curl_admin "${ADMIN}/api/admin/proxy/directory/api/v1/directory/organizations" | jq .
}

# ──────────────────────────────────────────────────────────────────────────────
sub="${1:-}"; shift || true
case "$sub" in
  onboard)            cmd_onboard "$@"            ;;
  list)               cmd_list "$@"               ;;
  get)                cmd_get "$@"                ;;
  offboard)           cmd_offboard "$@"           ;;
  mint-jwt)           cmd_mint_jwt "$@"           ;;
  patient-discovery)  cmd_patient_discovery "$@"  ;;
  document-query)     cmd_document_query "$@"     ;;
  document-retrieve)  cmd_document_retrieve "$@"  ;;
  message-delivery)   cmd_message_delivery "$@"   ;;
  policy-rules)       cmd_policy_rules            ;;
  directory-orgs)     cmd_directory_orgs          ;;
  *)
    sed -n '4,25p' "$0"
    exit 1
    ;;
esac
