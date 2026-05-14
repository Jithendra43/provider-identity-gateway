# TEFCA Gateway — E2E Test Harness

Tools for end-to-end testing of the gateway. Two target environments are
supported via env vars:

|             | Prod (default)                               | Dedicated test env                                  |
| ----------- | -------------------------------------------- | --------------------------------------------------- |
| `GATEWAY_URL` | `https://provider-identity-gw.c-hit.ai`     | `https://provider-identity-gw-test.c-hit.ai`        |
| `ADMIN_URL`   | `https://provider-identity-gw.c-hit.ai:8444`| `https://provider-identity-gw-test.c-hit.ai:8444`   |
| Partner cert  | self-signed (admin-only smoke tests)        | CA-signed by the E2E Test CA (real mTLS handshake)  |
| Partner JWT   | issued by external partner OIDC             | minted via the in-app test IdP (`mint-jwt`)         |

> **Why two envs?** The prod ALB trust store rejects unrecognized leaves at
> the TLS handshake (`Connection reset by peer` after Finished). The
> dedicated **test** env (see [infra/terraform/envs/test](../../infra/terraform/envs/test/terraform.tfvars))
> registers the **E2E Test Partner CA** as a trust anchor so leaves you mint
> locally are accepted. The same env enables `tefca.test-idp.token-endpoint-enabled=true`
> so `mint-jwt` works.

---

## Files

| File | Purpose |
| --- | --- |
| `gen-test-ca.sh` | One-time: mint the E2E Test Partner CA (RSA-4096, 10y) |
| `gen-partner-cert.sh` | Mint a partner leaf, self-signed (default) or `--ca`-signed |
| `e2e-prod.sh` | Onboard → list → get → offboard → mint-jwt → TEFCA calls |
| `TEFCA-Gateway-PROD.postman_collection.json` | Postman collection (admin + partner) |

---

## 1. One-time bootstrap of the test env

```sh
# (a) Mint the E2E Test CA on your operator workstation
./gen-test-ca.sh

# (b) Register it with terraform — drop the cert into the test env's certs/
cp out/e2e-test-ca.pem ../../infra/terraform/envs/test/certs/

# (c) Back up the CA private key to SSM SecureString (operator custody)
aws ssm put-parameter \
  --name /tefca/test/e2e-test-ca-private \
  --type SecureString --value "$(cat out/e2e-test-ca.key)" \
  --overwrite

# (d) Apply the test env (creates ALB trust store with the CA bundle)
terraform -chdir=../../infra/terraform/envs/test init
terraform -chdir=../../infra/terraform/envs/test apply
```

The `aws_lb_trust_store` has `replace_triggered_by` on the bundle etag, so
adding/rotating the CA forces a clean replacement.

---

## 2. Get an admin session cookie

The admin UI authenticates via Cognito Hosted UI. Easiest path:

1. Open `${ADMIN_URL}/admin/` in your browser, log in.
2. DevTools → Application → Cookies → copy the `SESSION` cookie value.
3. Export it for the scripts:
   ```sh
   export ADMIN_SESSION='SESSION=NWUz...'
   export GATEWAY_URL='https://provider-identity-gw-test.c-hit.ai'
   export ADMIN_URL='https://provider-identity-gw-test.c-hit.ai:8444'
   ```

---

## 3. Onboard a CA-signed test partner

```sh
# Mint a CA-signed leaf (chain emitted as out/<ORG>.pem)
./gen-partner-cert.sh ORG-E2E-001 "E2E QHIN Partner" --ca

# Onboard via admin API — the chain (.pem, leaf+CA) is sent to the gateway
./e2e-prod.sh onboard ORG-E2E-001 "E2E QHIN Partner"
```

Save the `partnerId` (`PARTNER-…`) from the response.

---

## 4. Mint a partner JWT via the in-app test IdP

Test env only — endpoint is gated by `tefca.test-idp.token-endpoint-enabled`.

```sh
./e2e-prod.sh mint-jwt ORG-E2E-001 NODE-E2E-001
# ✔ minted — export PARTNER_JWT=eyJhbGciOi...
eval "$(./e2e-prod.sh mint-jwt ORG-E2E-001 NODE-E2E-001 | grep '^export ')"
```

Or skip the manual mint — the TEFCA subcommands auto-mint when `PARTNER_JWT`
is unset (using `$ADMIN_SESSION` against the admin proxy):

```sh
unset PARTNER_JWT
./e2e-prod.sh patient-discovery ORG-E2E-001    # auto-mints, then calls
```

---

## 5. Call TEFCA endpoints

```sh
./e2e-prod.sh patient-discovery ORG-E2E-001
./e2e-prod.sh document-query    ORG-E2E-001
./e2e-prod.sh document-retrieve ORG-E2E-001
./e2e-prod.sh message-delivery  ORG-E2E-001
```

Each call:
- Presents `out/ORG-E2E-001.{crt,key}` as the client cert (chain matches the
  CA in the ALB trust store, so handshake succeeds).
- Sends a Bearer JWT signed by the in-app test IdP whose `iss`/`aud` match
  the gateway's resource-server config (token validates).

---

## 6. Offboard

```sh
./e2e-prod.sh offboard PARTNER-<id>
```

(Validated — DELETE returns **204** after the jsonb cast fix in commit `764e5b1`.)

---

## 7. Postman quick start

1. Import `TEFCA-Gateway-PROD.postman_collection.json`.
2. Set collection variables:
   - `gw`     → `https://provider-identity-gw-test.c-hit.ai` (or prod)
   - `admin`  → `https://provider-identity-gw-test.c-hit.ai:8444`
   - `adminSessionCookie` → `SESSION=...`
   - `orgId`, `partnerName`, `certificatePem`
3. In Postman → Settings → Certificates: add a client cert for host
   `provider-identity-gw-test.c-hit.ai` pointing at `out/<ORG>.{crt,key}`.
4. Run folders top-to-bottom. Folder 4's first request mints `partnerJwt`
   automatically; subsequent requests inherit it.

---

## 8. Operator key custody

- `out/e2e-test-ca.key` — primary copy on your workstation, `chmod 600`,
  never committed (`.gitignore`'d).
- Backup encrypted in **SSM SecureString** at `/tefca/test/e2e-test-ca-private`.
- Rotation: delete `out/e2e-test-ca.{key,crt}`, rerun `./gen-test-ca.sh`,
  re-apply terraform (trust store auto-replaces on bundle etag change),
  reissue all leaves with `--ca`, re-onboard partners.
