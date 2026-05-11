#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# gen-certs.sh — generate the local mTLS PKI used by docker-compose and Postman
# ──────────────────────────────────────────────────────────────────────────────
# Produces, under test-harness/certs/:
#   rootCA.{key,crt}            — local Trusted Exchange Framework root CA
#   server.{key,crt}            — ingress server cert (CN=localhost, SAN inc. tefca-ingress)
#   <partner>.{key,crt,p12}     — one client keypair per partner organisation
#   R004__partner_certificates.sql — SQL seed that registers every client
#                                    cert (real SHA-256 thumbprint) against
#                                    its partner_id so PartnerTrustStore
#                                    accepts it the moment the stack boots.
#
# Idempotent — re-running regenerates everything (cert validity is 825 days).
#
# Required tools: openssl, awk.  Tested with LibreSSL 3.x + OpenSSL 3.x.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

CERT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_OUT="$(cd "$CERT_DIR/../../database/seed" && pwd)/R004__partner_certificates.sql"
P12_PASS="${TEFCA_MTLS_P12_PASSWORD:-tefca-test}"
DAYS=825

cd "$CERT_DIR"

red()   { printf "\033[31m%s\033[0m\n" "$*"; }
green() { printf "\033[32m%s\033[0m\n" "$*"; }
info()  { printf "\033[36m▶ %s\033[0m\n" "$*"; }

# Per-partner identity — partner_id, org_id, common name, friendly name
# Aligns with the orgs seeded in R001/R003 so the directory recognises them.
PARTNERS=(
  "PART-CW-001|ORG-QHIN-001|ORG-QHIN-001.qhin.commonwellalliance.local|CommonWell QHIN"
  "PART-EHX-001|ORG-QHIN-002|ORG-QHIN-002.qhin.ehealthexchange.local|eHealth Exchange QHIN"
  "PART-EPIC-001|ORG-QHIN-003|ORG-QHIN-003.qhin.epic.local|Epic Nexus QHIN"
)

# ──────────────────────────────────────────────────────────────────────────────
# Step 1 — Root CA
# ──────────────────────────────────────────────────────────────────────────────
info "Generating local TEFCA test Root CA"
openssl genrsa -out rootCA.key 4096 2>/dev/null
openssl req -x509 -new -nodes -key rootCA.key -sha256 -days $((DAYS*4)) \
  -subj "/C=US/ST=DC/O=C-HIT TEFCA Test CA/OU=Local Development Only/CN=TEFCA Local Root CA" \
  -out rootCA.crt 2>/dev/null

# ──────────────────────────────────────────────────────────────────────────────
# Step 2 — Server cert for the ingress sidecar (NGINX terminates TLS here)
# ──────────────────────────────────────────────────────────────────────────────
info "Generating ingress server cert (CN=localhost, SAN: localhost, tefca-ingress)"
cat > server.cnf <<'EOF'
[ req ]
default_bits       = 2048
prompt             = no
distinguished_name = dn
req_extensions     = v3_req
[ dn ]
C  = US
ST = DC
O  = C-HIT TEFCA Gateway
OU = Local Ingress
CN = localhost
[ v3_req ]
subjectAltName = @alt
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
[ alt ]
DNS.1 = localhost
DNS.2 = tefca-ingress
DNS.3 = nginx-mtls
IP.1  = 127.0.0.1
EOF
openssl genrsa -out server.key 2048 2>/dev/null
openssl req -new -key server.key -out server.csr -config server.cnf 2>/dev/null
openssl x509 -req -in server.csr -CA rootCA.crt -CAkey rootCA.key -CAcreateserial \
  -out server.crt -days "$DAYS" -sha256 -extfile server.cnf -extensions v3_req 2>/dev/null
rm -f server.csr server.cnf

# ──────────────────────────────────────────────────────────────────────────────
# Step 3 — Partner client certs (one per QHIN). Real SHA-256 fingerprints used.
# ──────────────────────────────────────────────────────────────────────────────
declare -a CERT_ROWS=()
declare -a PARTNER_ROWS=()
declare -a OAUTH_ROWS=()
declare -a RL_ROWS=()

for entry in "${PARTNERS[@]}"; do
  IFS='|' read -r PARTNER_ID ORG_ID CN FRIENDLY <<< "$entry"
  info "Generating partner client cert for $ORG_ID  (CN=$CN)"

  cat > "${PARTNER_ID}.cnf" <<EOF
[ req ]
default_bits       = 2048
prompt             = no
distinguished_name = dn
req_extensions     = v3_req
[ dn ]
C  = US
ST = DC
O  = ${FRIENDLY}
OU = TEFCA QHIN Partner
CN = ${CN}
[ v3_req ]
keyUsage = digitalSignature, keyEncipherment, keyAgreement
extendedKeyUsage = clientAuth
EOF

  openssl genrsa -out "${PARTNER_ID}.key" 2048 2>/dev/null
  openssl req -new -key "${PARTNER_ID}.key" -out "${PARTNER_ID}.csr" -config "${PARTNER_ID}.cnf" 2>/dev/null
  openssl x509 -req -in "${PARTNER_ID}.csr" -CA rootCA.crt -CAkey rootCA.key -CAcreateserial \
    -out "${PARTNER_ID}.crt" -days "$DAYS" -sha256 -extfile "${PARTNER_ID}.cnf" -extensions v3_req 2>/dev/null

  # Bundle into PKCS12 for Postman / Java keystore consumption
  openssl pkcs12 -export -out "${PARTNER_ID}.p12" -inkey "${PARTNER_ID}.key" \
    -in "${PARTNER_ID}.crt" -certfile rootCA.crt \
    -name "${PARTNER_ID}" -passout "pass:${P12_PASS}" 2>/dev/null

  rm -f "${PARTNER_ID}.csr" "${PARTNER_ID}.cnf"

  # Real cert metadata extracted directly from the issued cert ─────────────
  THUMB=$(openssl x509 -in "${PARTNER_ID}.crt" -fingerprint -sha256 -noout \
            | awk -F= '{gsub(":","",$2); print tolower($2)}')
  SUBJECT=$(openssl x509 -in "${PARTNER_ID}.crt" -subject -noout \
            | sed -E 's/^subject= *//; s/^subject=//')
  ISSUER=$(openssl x509 -in "${PARTNER_ID}.crt" -issuer -noout \
            | sed -E 's/^issuer= *//; s/^issuer=//')
  SERIAL=$(openssl x509 -in "${PARTNER_ID}.crt" -serial -noout | cut -d= -f2)
  NB=$(openssl x509 -in "${PARTNER_ID}.crt" -startdate -noout | cut -d= -f2)
  NA=$(openssl x509 -in "${PARTNER_ID}.crt" -enddate -noout | cut -d= -f2)
  NB_ISO=$(date -j -u -f "%b %e %T %Y %Z" "$NB" "+%Y-%m-%d %H:%M:%S+00" 2>/dev/null \
            || date -u -d "$NB" "+%Y-%m-%d %H:%M:%S+00")
  NA_ISO=$(date -j -u -f "%b %e %T %Y %Z" "$NA" "+%Y-%m-%d %H:%M:%S+00" 2>/dev/null \
            || date -u -d "$NA" "+%Y-%m-%d %H:%M:%S+00")

  PARTNER_ROWS+=("'${ORG_ID}'|CERT-LOCAL-${PARTNER_ID}|${THUMB}|${SUBJECT//\'/\'\'}|${ISSUER//\'/\'\'}|${SERIAL}|${NB_ISO}|${NA_ISO}")

  green "    ✓ $ORG_ID  thumbprint=${THUMB:0:16}…"
done

# ──────────────────────────────────────────────────────────────────────────────
# Step 4 — Emit R004 seed: registers every partner + cert + OAuth + rate-limit.
# Idempotent (ON CONFLICT DO NOTHING) so re-running is safe.
# ──────────────────────────────────────────────────────────────────────────────
info "Writing seed → ${SQL_OUT}"
{
  cat <<'HDR'
-- ───────────────────────────────────────────────────────────────────────────
-- R004__partner_certificates.sql  (auto-generated by gen-certs.sh)
-- ───────────────────────────────────────────────────────────────────────────
-- Registers locally-issued QHIN partner client certificates against the
-- partners that R003 already seeded (looked up by org_id). The trust store
-- (PartnerCertificateLoader) reads these rows on startup and on its 1-min
-- refresh tick, populating the in-memory thumbprint set used by the
-- MtlsValidationFilter.
--
-- Idempotent: ON CONFLICT (thumbprint) DO NOTHING so re-running the cert
-- generator after a partial boot does not cause a Flyway/init-db crash.
-- DO NOT EDIT BY HAND — run `make certs` to regenerate.
-- ───────────────────────────────────────────────────────────────────────────

HDR
  for row in "${PARTNER_ROWS[@]}"; do
    IFS='|' read -r ORG_LIT CERT_ID THUMB SUBJECT ISSUER SERIAL NB NA <<< "$row"
    cat <<SQL
INSERT INTO ingress.partner_certificates
    (certificate_id, partner_id, thumbprint, subject_dn, issuer_dn, serial_number, not_before, not_after, active)
SELECT '${CERT_ID}', p.partner_id, '${THUMB}', '${SUBJECT}', '${ISSUER}', '${SERIAL}', '${NB}', '${NA}', TRUE
FROM ingress.partners p
WHERE p.org_id = ${ORG_LIT}
ON CONFLICT (thumbprint) DO NOTHING;

SQL
  done
} > "$SQL_OUT"

# ──────────────────────────────────────────────────────────────────────────────
# Step 5 — Permissions + summary
# ──────────────────────────────────────────────────────────────────────────────
chmod 644 *.crt *.p12 || true
chmod 600 *.key       || true
rm -f rootCA.srl

green ""
green "All cert material is in $CERT_DIR"
green "  Root CA           : rootCA.crt"
green "  Server (ingress)  : server.crt / server.key"
for entry in "${PARTNERS[@]}"; do
  IFS='|' read -r PARTNER_ID ORG_ID CN _ <<< "$entry"
  green "  Partner ${ORG_ID}: ${PARTNER_ID}.{key,crt,p12}  (PKCS12 password: ${P12_PASS})"
done
green ""
green "Seed file written : ${SQL_OUT}"
green "Next: ./test-harness/certs already mounted by docker-compose. Run \`make up-mtls\`."
