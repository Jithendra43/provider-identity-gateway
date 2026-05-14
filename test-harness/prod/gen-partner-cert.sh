#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# gen-partner-cert.sh — mint a partner leaf cert for onboarding tests
# ──────────────────────────────────────────────────────────────────────────────
# Two signing modes:
#   (default) self-signed leaf — works only against the local docker-compose
#             setup or any env where the ALB trust store admits this exact
#             leaf as a trust anchor.
#   --ca      sign with the E2E Test Partner CA produced by gen-test-ca.sh.
#             Use this for any AWS environment whose trust store contains
#             the E2E Test CA (e.g. infra/terraform/envs/test).
#
# Usage:
#   ./gen-partner-cert.sh <ORG_ID> "<Display Name>"            # self-signed
#   ./gen-partner-cert.sh <ORG_ID> "<Display Name>" --ca       # CA-signed
#   CA_KEY=/path/to/e2e-test-ca.key CA_CRT=/path/to/e2e-test-ca.crt \
#     ./gen-partner-cert.sh <ORG_ID> "<Display Name>" --ca     # explicit CA
#
# Produces under ./out/:
#   <ORG_ID>.key   RSA-2048 private key
#   <ORG_ID>.crt   leaf cert only (PEM)
#   <ORG_ID>.pem   chain — leaf first, then E2E Test CA (when --ca);
#                  identical to .crt when self-signed
#
# The PEM must be POSTed in OnboardPartnerRequest.certificatePem.
# The gateway computes the SHA-256 thumbprint server-side from the leaf.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ORG_ID="${1:?usage: $0 <ORG_ID> <Display Name> [--ca]}"
DISPLAY="${2:?usage: $0 <ORG_ID> <Display Name> [--ca]}"
MODE="${3:-self}"

OUT="$(cd "$(dirname "$0")" && pwd)/out"
mkdir -p "$OUT"

KEY="$OUT/${ORG_ID}.key"
CRT="$OUT/${ORG_ID}.crt"
PEM="$OUT/${ORG_ID}.pem"

CN="${ORG_ID}.partner.c-hit.ai"

cat > "$OUT/${ORG_ID}.cnf" <<EOF
[ req ]
default_bits       = 2048
prompt             = no
distinguished_name = dn
req_extensions     = v3_req
[ dn ]
C  = US
ST = DC
O  = ${DISPLAY}
OU = TEFCA Test Partner
CN = ${CN}
[ v3_req ]
subjectAltName     = DNS:${CN}
keyUsage           = digitalSignature, keyEncipherment
extendedKeyUsage   = clientAuth
EOF

openssl genrsa -out "$KEY" 2048 2>/dev/null

if [[ "$MODE" == "--ca" ]]; then
  CA_KEY="${CA_KEY:-$OUT/e2e-test-ca.key}"
  CA_CRT="${CA_CRT:-$OUT/e2e-test-ca.crt}"
  if [[ ! -f "$CA_KEY" || ! -f "$CA_CRT" ]]; then
    echo "✘ --ca mode but CA material missing: $CA_KEY / $CA_CRT"
    echo "  Run ./gen-test-ca.sh first, or set CA_KEY/CA_CRT env vars."
    exit 1
  fi
  openssl req -new -key "$KEY" -out "$OUT/${ORG_ID}.csr" \
    -config "$OUT/${ORG_ID}.cnf" 2>/dev/null
  openssl x509 -req \
    -in "$OUT/${ORG_ID}.csr" \
    -CA "$CA_CRT" -CAkey "$CA_KEY" -CAcreateserial \
    -out "$CRT" -days 825 -sha256 \
    -extfile "$OUT/${ORG_ID}.cnf" -extensions v3_req 2>/dev/null
  cat "$CRT" "$CA_CRT" > "$PEM"
  rm -f "$OUT/${ORG_ID}.csr" "$OUT/${ORG_ID}.cnf"
  ISSUER_LINE="$(openssl x509 -in "$CA_CRT" -noout -subject | sed 's/^subject= //')"
else
  openssl req -new -x509 -key "$KEY" -out "$CRT" -days 825 -sha256 \
    -config "$OUT/${ORG_ID}.cnf" -extensions v3_req 2>/dev/null
  cp "$CRT" "$PEM"
  rm -f "$OUT/${ORG_ID}.cnf"
  ISSUER_LINE="self-signed"
fi

THUMB=$(openssl x509 -in "$CRT" -noout -fingerprint -sha256 \
  | awk -F= '{print $2}' | tr -d ':' | tr 'A-F' 'a-f')

echo "✔ Generated cert for ${ORG_ID}"
echo "  Mode   : ${MODE}"
echo "  Issuer : ${ISSUER_LINE}"
echo "  Key    : $KEY"
echo "  Cert   : $CRT"
echo "  Chain  : $PEM"
echo "  CN     : $CN"
echo "  SHA256 : $THUMB"
echo
echo "Use with:"
echo "  curl --cert '$CRT' --key '$KEY' \"\${GATEWAY_URL:-https://provider-identity-gw.c-hit.ai}/api/v1/tefca/...\""
