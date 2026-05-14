#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# gen-test-ca.sh — mint a dedicated E2E Test Partner CA
# ──────────────────────────────────────────────────────────────────────────────
# Usage:
#   ./gen-test-ca.sh                        # uses default CN
#   ./gen-test-ca.sh "C-HIT E2E Test CA"
#
# Produces under ./out/:
#   e2e-test-ca.key   (RSA-4096 private key — keep SECRET, do not commit)
#   e2e-test-ca.crt   (X.509 v3 CA cert, 10 years, CA:TRUE, KU=keyCertSign+cRLSign)
#   e2e-test-ca.pem   (alias of .crt — what you upload to the trust store / paste
#                      into terraform.tfvars trusted_partner_cas)
#
# Operator runbook (one-time per environment):
#   1) ./gen-test-ca.sh
#   2) Copy the contents of out/e2e-test-ca.crt
#   3) Add it to infra/terraform/envs/test/terraform.tfvars under
#        trusted_partner_cas = { e2e_test_ca = file("certs/e2e-test-ca.pem") }
#      OR upload directly to s3://<prefix>-trust-store/partners/e2e-test.pem and
#      append into partners/_bundle.pem
#   4) terraform -chdir=infra/terraform/envs/test apply
#      (the aws_lb_trust_store has replace_triggered_by on the bundle etag)
#   5) Stash out/e2e-test-ca.key in your password manager / SSM SecureString:
#        aws ssm put-parameter \
#          --name /tefca/test/e2e-test-ca-private \
#          --type SecureString --value "$(cat out/e2e-test-ca.key)"
#      The KEY MUST NOT be committed.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

CN="${1:-C-HIT E2E Test Partner CA}"
OUT="$(cd "$(dirname "$0")" && pwd)/out"
mkdir -p "$OUT"

KEY="$OUT/e2e-test-ca.key"
CRT="$OUT/e2e-test-ca.crt"
PEM="$OUT/e2e-test-ca.pem"

if [[ -f "$KEY" || -f "$CRT" ]]; then
  echo "✘ refusing to overwrite existing $OUT/e2e-test-ca.{key,crt}"
  echo "  (delete them manually if you really intend to rotate the test CA)"
  exit 1
fi

cat > "$OUT/e2e-test-ca.cnf" <<EOF
[ req ]
default_bits       = 4096
prompt             = no
distinguished_name = dn
x509_extensions    = v3_ca
[ dn ]
C  = US
ST = DC
O  = C-HIT
OU = TEFCA E2E Test Authority
CN = ${CN}
[ v3_ca ]
basicConstraints       = critical,CA:TRUE
keyUsage               = critical,keyCertSign,cRLSign
subjectKeyIdentifier   = hash
authorityKeyIdentifier = keyid:always
EOF

openssl genrsa -out "$KEY" 4096 2>/dev/null
openssl req -new -x509 -key "$KEY" -out "$CRT" -days 3650 -sha256 \
  -config "$OUT/e2e-test-ca.cnf" -extensions v3_ca 2>/dev/null
cp "$CRT" "$PEM"
rm -f "$OUT/e2e-test-ca.cnf"
chmod 600 "$KEY"
chmod 644 "$CRT" "$PEM"

THUMB=$(openssl x509 -in "$CRT" -noout -fingerprint -sha256 \
  | awk -F= '{print $2}' | tr -d ':' | tr 'A-F' 'a-f')

echo "✔ Generated E2E Test Partner CA"
echo "  CN     : ${CN}"
echo "  Key    : $KEY  (chmod 600 — keep SECRET)"
echo "  Cert   : $CRT"
echo "  PEM    : $PEM"
echo "  SHA256 : $THUMB"
echo
echo "Next steps:"
echo "  1) Add to infra/terraform/envs/test/terraform.tfvars:"
echo "       trusted_partner_cas = { e2e_test_ca = file(\"certs/e2e-test-ca.pem\") }"
echo "  2) terraform -chdir=infra/terraform/envs/test apply"
echo "  3) Mint partner leaves with:"
echo "       ./gen-partner-cert.sh ORG-E2E-001 \"E2E Partner\" --ca"
