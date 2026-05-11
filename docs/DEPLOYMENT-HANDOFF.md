# TEFCA Gateway — Deployment & Architecture Handoff

> Audience: engineer picking up the project for final touches and ongoing
> deployments. This is the "everything you need in one place" doc — cross-links
> the deeper references where useful.
>
> **Live environment (as of this handoff):** `prod` — see §6.
> **Source of truth:** `main` branch on the GitHub repo.
> **Latest released image tag:** `v20260505-prod-ready` (no-cache HTML headers fix browser-cache invisibility of Edit URL; PA hot-path tuned to 450ms timeout with dedicated reactor-netty pool, in-process LocalEndpointResolver, refreshAfterWrite policy cache, startup CacheWarmer; all caches in-process Caffeine — no Redis / ElastiCache).

---

## 1. What this thing is

A single Spring Boot 3.3.5 / Java 21 fat-jar (`tefca-gateway-app`) that serves
two **completely separate** workloads on the same JVM. They are split apart
at the AWS ALB by **listener port** — there is no path overlap between them:

| Listener | Surface                  | TLS mode                       | Who connects                                  | What it serves |
| -------- | ------------------------ | ------------------------------ | --------------------------------------------- | -------------- |
| `:443`   | **Public partner API**   | mTLS — partner client cert required | QHIN / SubP partner systems (machine-to-machine) | TEFCA exchange (`/api/v1/tefca/**`), Prior Authorization (`/api/v1/pa/**`) |
| `:8444`  | **Operator-only admin**  | TLS only (no client cert) — Cognito OIDC + MFA at the app layer | C-HIT operators (humans, browser)             | Admin UI (`/admin/**`), admin proxy (`/api/admin/proxy/**`), `actuator/**` |

**Partner traffic never touches `:8444`, and operator traffic never touches
`:443`.** The two listeners have different security groups, different ALB
listener rules, and different filter chains in the JVM. A partner that
accidentally targets `:8444` will be rejected at the Cognito redirect; an
operator that targets `:443` will fail the mTLS handshake at the ALB.

Inside the JVM, four logical "services" (`ingress-auth`, `policy`, `routing`,
`directory-cache`) call each other in-process over `127.0.0.1:8080`. They are
still kept as separate Maven modules so the team can split them back into
discrete Fargate tasks later if scale demands it.

### 1.1 Where mTLS is actually verified

Defense-in-depth, two stages:

1. **AWS ALB (`:443`, edge):** terminates TLS, performs the mTLS handshake,
   and verifies the partner's client certificate against the ALB **trust
   store** (a bundle of partner-CA / partner-leaf PEMs uploaded by Terraform
   in `infra/terraform/modules/alb/truststore/`). A handshake that doesn't
   chain to a trusted issuer is **dropped at the ALB** — the request never
   reaches the JVM. This is the cheap, fast first gate.

2. **`ingress-auth-service` (in-JVM, [`MtlsValidationFilter`](../services/ingress-auth-service/src/main/java/chit/tefca/ingress/filter/MtlsValidationFilter.java)):**
   the ALB forwards the verified cert in the `X-Amzn-Mtls-Clientcert` /
   `X-Client-Cert` header. The filter:
   - rejects if the sidecar/ALB reports `X-Client-Verify != SUCCESS`,
   - rejects if `tefca.mtls.strict=true` (prod default) and no cert header
     is present at all (means the ingress edge is misconfigured),
   - re-validates the cert against the JVM-side `PartnerTrustStore`,
   - resolves the leaf cert's thumbprint to a `partner_id` row via
     [`CertificateOrgMapper`](../services/ingress-auth-service/src/main/java/chit/tefca/ingress/security/CertificateOrgMapper.java),
     and sets that as the authenticated principal for the rest of the chain.

The admin chain (`:8444`) **never runs `MtlsValidationFilter`** — see the
filter's `shouldNotFilter`, which skips `/admin/**`, `/api/admin/**`,
`/actuator/**`, and the swagger paths. The admin chain authenticates with a
Cognito-issued JWT cookie session instead.

---

## 2. Repository layout

```
tefca-gateway/
├── apps/admin-ui/              Next.js 14 admin console (statically exported)
├── services/
│   ├── tefca-gateway-app/       👈 the deployable fat-jar (+ Dockerfile)
│   ├── ingress-auth-service/   Public API controllers, admin proxy, JWT/HMAC
│   ├── policy-service/         Chain-of-responsibility policy engine
│   ├── routing-service/        Endpoint resolver + downstream forwarder
│   ├── directory-cache-service/ Org/Node/Endpoint registry + Redis cache
│   └── tefca-common/           Shared models, enums, audit, exceptions
├── infra/
│   ├── terraform/envs/prod/    The only deployable Terraform stack
│   ├── terraform/modules/      network, alb, ecs, rds, s3, secrets,
│   │                            iam, kms, cognito, acm, observability
│   ├── bootstrap.sh            Creates S3 state bucket + Dynamo lock table
│   └── README.md               Production deploy walkthrough
├── database/                   Local-only Postgres init/migration/seed
│                                (used by docker-compose; prod uses Flyway
│                                 inside the fat-jar at boot)
├── test-harness/               Mock OIDC, WireMock partners, partner certs
├── docker-compose.yml          Local dev stack (5 svcs + pg + redis + mocks)
├── Makefile                    Local dev driver
└── .github/workflows/
    ├── ci.yml                  Build + tests + SpotBugs + OWASP scan
    ├── deploy.yml              Build → push to ECR → force-new-deploy ECS
    ├── infra-plan.yml          terraform plan on PR
    ├── infra-apply.yml         terraform apply on main (manual gate)
    └── backup-restore-drill.yml  Quarterly DR drill
```

---

## 3. Request flow

### 3a. Partner / public API (`:443`, mTLS)

```
┌─────────────┐  mTLS POST /api/v1/pa/PA_ORDER_SIGN
│ Partner EHR │ ────────────────────────────────────►  ALB :443  ◄── partner mTLS endpoint
└─────────────┘    cert: PART-EPIC-001                  │          (NOT the admin endpoint)
                                                        │
                                       (1) ALB verifies cert against
                                           partner truststore. Bad
                                           handshake → dropped here.
                                                        ▼
                                         ┌─────────────────────────────┐
                                         │ ingress-auth (filter chain) │
                                         │ ① MtlsValidationFilter:     │
                                         │   re-check X-Amzn-Mtls-     │
                                         │   Clientcert against        │
                                         │   PartnerTrustStore →       │
                                         │   CertificateOrgMapper →    │
                                         │   partner_id principal      │
                                         │ ② JWT/HMAC scheme injection │
                                         │ ③ X-Correlation-ID stamp    │
                                         │ ④ Idempotency-Key check     │
                                         └────────────┬────────────────┘
                                                      │ in-process
                                                      ▼
                                         ┌─────────────────────────────┐
                                         │  policy-service             │
                                         │  ChainOfResponsibility:     │
                                         │   purpose-of-use → consent  │
                                         │   → org allowlist → ABAC    │
                                         └────────────┬────────────────┘
                                                      ▼ ALLOW
                                         ┌─────────────────────────────┐
                                         │  routing-service            │
                                         │  EndpointResolver:          │
                                         │   operation → modality      │
                                         │   → Redis-cached lookup of  │
                                         │     directory_endpoints     │
                                         └────────────┬────────────────┘
                                                      ▼
                                         ┌─────────────────────────────┐
                                         │  TransactionForwarder       │
                                         │  WebClient → endpoint.url   │
                                         │  (currently → Mock PA       │
                                         │   /mock-pa/cds-services/…)  │
                                         └────────────┬────────────────┘
                                                      ▼
                                         ┌─────────────────────────────┐
                                         │  audit (S3 WORM, 6 yr)       │
                                         │  one event per phase        │
                                         └─────────────────────────────┘
```

Same flow for `/api/v1/tefca/**` (patient discovery, document query, document
retrieve, message delivery) — the only difference is which `operation` value
the policy/routing pipeline sees.

### 3b. Operator / admin UI (`:8444`, no client cert)

```
┌──────────────┐   HTTPS GET /admin/  (no client cert)
│  Operator    │ ───────────────────────────────────────►  ALB :8444  ◄── admin endpoint
│  browser     │                                            │            (NOT the partner endpoint)
└──────────────┘                                            ▼
                                                ┌────────────────────────────────┐
                                                │ Spring Security adminChain     │
                                                │  ① OAuth2 login →              │
                                                │     Cognito hosted UI + MFA    │
                                                │  ② JWT cookie session          │
                                                │  ③ ROLE_QHIN_ADMIN /           │
                                                │     ROLE_QHIN_VIEWER required  │
                                                └─────────────┬──────────────────┘
                                                              │ in-process
                                                              ▼
                                                ┌────────────────────────────────┐
                                                │ AdminProxyController           │
                                                │  /api/admin/proxy/{svc}/**     │
                                                │  → injects bearer JWT          │
                                                │  → forwards to in-JVM admin    │
                                                │    controller (e.g. PATCH      │
                                                │    /api/v1/admin/directory/    │
                                                │    endpoints/{id})             │
                                                └────────────────────────────────┘
```

The admin chain shares **no** filters with the partner chain.
`MtlsValidationFilter` explicitly skips `/admin/**` and `/api/admin/**`; the
admin chain rejects unauthenticated requests with a Cognito redirect rather
than an `MTLS_REQUIRED` error.

### Operation → endpoint mapping

The routing layer never has a hard-coded URL. It looks up
`directory_endpoints` rows by `(operation, modality, active=TRUE)`. Endpoints
are seeded by Flyway migrations:

| Migration | Purpose |
| --------- | ------- |
| `V010__directory_seed.sql`   | Bootstrap orgs + nodes (QHINs, SubP) |
| `V011__pa_endpoint_seed.sql` | All 14 PA endpoints (CRD/DTR/PAS), seeded `active=FALSE` |
| `V012__pa_partner_seed.sql`  | C-HIT PA Platform org + Epic partner registration |
| `V013__activate_dtr_pas_endpoints.sql` | Flips DTR + PAS endpoints to `active=TRUE` once Mock PA controller landed |

To re-point a registered endpoint at a real partner URL after go-live, **do
not write a new migration** — use the admin UI (§5).

---

## 4. AWS deployment

### One-time bootstrap

```bash
export AWS_PROFILE=tefca-rebuild       # or whatever profile owns the account
export AWS_DEFAULT_REGION=us-east-1
export AWS_PAGER=

./infra/bootstrap.sh
# → creates S3 state bucket "tefca-gw-tfstate-<accountId>"
# → creates DynamoDB lock table "tefca-gw-tflock"
# → writes infra/terraform/envs/prod/backend.hcl
# → prints a fresh HMAC secret — SAVE IN YOUR PASSWORD MANAGER
```

### First-time apply

```bash
cd infra/terraform/envs/prod
export TF_VAR_hmac_secret_initial="<the secret bootstrap.sh printed>"

terraform init -backend-config=backend.hcl
terraform plan -out tfplan
terraform apply tfplan
```

This builds: VPC (2 AZs), ALB (`:443` mTLS truststore + `:8444` TLS), ECS
cluster `tefca-gw-prod`, the single ECS service `tefca-gw-prod` running
the fat-jar on Fargate ARM64 (1 vCPU / 2 GB), RDS PostgreSQL `t4g.micro`,
S3 audit bucket (WORM, KMS), Cognito user pool for admin SSO, and CloudWatch
dashboard.

> The first apply has no image in ECR yet. The ECS service will start the
> "wait" loop. Push an image (next step) and ECS will converge.

### Application deploy (every release after the first)

Two ways:

**A. GitHub Actions (preferred):** push to `main` (or `workflow_dispatch`)
the [.github/workflows/deploy.yml](../.github/workflows/deploy.yml) job runs:

1. `docker buildx build -f services/tefca-gateway-app/Dockerfile --platform linux/arm64`
2. Push to ECR `tefca-gw-prod/gateway:${git_sha}` and `:latest`
3. `aws ecs update-service --cluster tefca-gw-prod --service tefca-gw-prod --force-new-deployment`
4. `aws ecs wait services-stable`

The `prod` GitHub environment provides a manual approval gate.

**B. Manual:**

```bash
export PATH="/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin:/usr/local/bin"
export AWS_PROFILE=tefca-rebuild AWS_PAGER= AWS_DEFAULT_REGION=us-east-1

cd services
mvn -pl tefca-gateway-app -am -DskipTests -T 1C package

cd ..
TAG="v$(date +%Y%m%d)-$(git rev-parse --short HEAD)"
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
REPO="${ACCOUNT}.dkr.ecr.us-east-1.amazonaws.com/tefca-gw-prod/gateway"

aws ecr get-login-password | docker login --username AWS --password-stdin "${REPO%/*}"
docker buildx build --platform linux/arm64 \
  -f services/tefca-gateway-app/Dockerfile \
  -t "${REPO}:${TAG}" -t "${REPO}:latest" --push .

aws ecs update-service --cluster tefca-gw-prod --service tefca-gw-prod \
  --force-new-deployment >/dev/null
aws ecs wait services-stable --cluster tefca-gw-prod --services tefca-gw-prod
```

### Notable env vars on the task

Set in `infra/terraform/modules/ecs` from Secrets Manager / Terraform vars:

| Var | Source | Notes |
| --- | ------ | ----- |
| `SPRING_PROFILES_ACTIVE` | task def | always `prod` |
| `SPRING_DATASOURCE_URL` | task def | RDS endpoint, `sslmode=require` |
| `SPRING_DATASOURCE_PASSWORD` | Secrets Manager | rotated by RDS managed rotation |
| `TEFCA_HMAC_SECRET` | Secrets Manager | rotated quarterly; bootstrap value seeded by `TF_VAR_hmac_secret_initial` |
| `TEFCA_AUDIT_S3_BUCKET` | task def | output from `s3` module |
| `TEFCA_OIDC_ISSUER_URI` | task def | Cognito user pool URL |
| `JAVA_TOOL_OPTIONS` | task def | `-XX:MaxRAMPercentage=70 -Djava.security.egd=file:/dev/./urandom` |

### BC-FIPS gotcha

The fat-jar Dockerfile (`services/tefca-gateway-app/Dockerfile`) **extracts**
the Bouncy Castle FIPS jars from the Spring Boot fat-jar layout into
`/app/lib` and prepends them to `--module-path`, because BC-FIPS refuses to
load when nested inside another jar. If you ever swap the Dockerfile, keep
this extraction step or FIPS startup will fail with
`org.bouncycastle.crypto.CryptoServicesPermission` errors.

---

## 5. Operating the gateway

### Admin UI access

```
URL:      https://tefca-gw-prod-554226928.us-east-1.elb.amazonaws.com:8444/admin/
Login:    Cognito SSO (MFA required)
Roles:    QHIN_ADMIN  → full read/write
          QHIN_VIEWER → read-only
```

### Editing endpoint URLs (the new bit)

The Directory page now lets `QHIN_ADMIN` operators rewrite `endpoint.url` and
toggle `active` **without applying a SQL migration**. This is the workflow you
will use to swap the bundled Mock PA endpoints for real partner URLs at
go-live.

1. Open `/admin/directory/`, **Organizations** or **Nodes** tab.
2. Select the org/node — the right-hand pane lists its endpoints.
3. Click **Edit** on the endpoint row.
4. Edit the URL (must be a valid `https://` URL for production partners),
   optionally toggle **Active**, then **Save**.
5. The backend writes to `directory.directory_endpoints`, invalidates the
   per-org Redis cache, and emits an audit event. The next routing call
   picks up the new URL within milliseconds.

Behind the scenes:

```
PATCH /api/v1/admin/directory/endpoints/{endpointId}
Authorization: Bearer <cognito jwt>            # injected by admin proxy
Content-Type:  application/json

{ "url": "https://epic.partner.example.com/PA/order-sign", "active": true }
```

This is the only mutable field set from the UI; `endpointId`, `nodeId`,
`modality`, `supportedOperations` are immutable identity attributes — to
change those, add a Flyway migration.

### Smoke test (14 PA endpoints)

```bash
URL=https://tefca-gw-prod-554226928.us-east-1.elb.amazonaws.com
CERT=test-harness/certs/PART-EPIC-001.crt
KEY=test-harness/certs/PART-EPIC-001.key

for ep in pa-crd pa-crd-select pa-appointment pa-order-dispatch \
          pa-encounter-start pa-encounter-discharge \
          dtr/questionnaire-package dtr/questionnaire-response \
          pas/claim-submit pas/claim-inquire; do
  code=$(curl -ksS --cert "$CERT" --key "$KEY" \
    -o /dev/null -w "%{http_code}" \
    -X POST "$URL/api/v1/pa/$ep" \
    -H "Content-Type: application/json" -d '{}')
  echo "$ep => $code"
done
```

All 10 should be `200`. (The earlier doc mentioned 14; that count includes
4 sub-paths the Mock PA controller does not implement — they are correctly
expected to be `404`.)

### Health & observability

| Probe | URL |
| ----- | --- |
| Liveness  | `https://<alb>:8444/actuator/health/liveness` |
| Readiness | `https://<alb>:8444/actuator/health/readiness` |
| Prometheus | `https://<alb>:8444/actuator/prometheus` (admin auth) |
| Audit (S3) | `s3://tefca-gw-prod-audit-<accountId>/year=YYYY/month=MM/day=DD/` |
| CloudWatch dashboard | "TEFCA Gateway — Prod" (created by `observability` module) |

---

## 6. Current production environment (snapshot)

| Item | Value |
| ---- | ----- |
| AWS account profile | `tefca-rebuild` |
| Region | `us-east-1` |
| ALB DNS | `tefca-gw-prod-554226928.us-east-1.elb.amazonaws.com` |
| ECR repo | `680780727322.dkr.ecr.us-east-1.amazonaws.com/tefca-gw-prod/gateway` |
| ECS cluster / service | `tefca-gw-prod` / `tefca-gw-prod` |
| Active image | `:v20260505-prod-ready` — task def `tefca-gw-prod:54`, rolled out 2026-05-05 (admin UI cache-control hardening, 450ms PA budget, in-process directory resolver, RedisDirectoryCache renamed to DirectoryCaffeineCache) |
| Secrets/env source-of-truth | [`docs/SECRETS-INVENTORY.md`](SECRETS-INVENTORY.md) + [`infra/terraform/envs/prod/terraform.tfvars.example`](../infra/terraform/envs/prod/terraform.tfvars.example) |
| Admin UI capabilities | List/create/update/delete directory **endpoints**; delete directory **nodes** (server returns 409 if endpoints still attached); modality dropdown covers all 20 supported modalities (TEFCA + PA + DTR + PAS); all admin writes go through Cognito MFA + ROLE_QHIN_ADMIN + audit log |
| Test partner cert | `test-harness/certs/PART-EPIC-001.{crt,key}` |
| Test org | `ORG-CHIT-PA-PLATFORM` (subscribing participant) |
| Admin SSO | Cognito user pool managed by `infra/terraform/modules/cognito` |

---

## 7. Local development

```bash
make up          # docker-compose up the 5 svcs + pg + redis + mocks
make seed        # apply database/seed/* (loads test orgs/policies/endpoints)
make smoke       # hit a representative TEFCA + PA path through the local stack
make logs        # tail all services
make down        # stop & remove
```

The local stack uses the per-service Dockerfiles (`services/{ingress-auth,
policy,routing,directory-cache}-service/Dockerfile`); production uses the
single fat-jar `services/tefca-gateway-app/Dockerfile`. Both code paths are
exercised by CI.

Local admin UI: <http://localhost:3000> (run `npm run dev` in
`apps/admin-ui/`); proxies through the local `ingress-auth-service`.

---

## 8. Hand-off checklist (things that still need a human)

- [ ] **Rotate the bootstrap HMAC secret.** The value seeded by
      `TF_VAR_hmac_secret_initial` is intentionally a placeholder
      (`bootstrap-temp-secret-rotate-me-immediately-after-go-live-xyz123`).
      Rotate via AWS Secrets Manager → `tefca/hmac/primary`, then bounce the
      ECS service.
- [ ] **Replace the test partner cert.** `PART-EPIC-001.{crt,key}` is a
      self-signed dev cert. Production partners' public certs go in the ALB
      mTLS truststore (`infra/terraform/modules/alb/truststore/*.pem` →
      `terraform apply` rebundles).
- [ ] **Re-point Mock PA endpoints** to real partner URLs once partner
      integrations are signed off — use the Directory page editor (§5).
- [ ] **Run the quarterly DR drill** — see
      [.github/workflows/backup-restore-drill.yml](../.github/workflows/backup-restore-drill.yml).
- [ ] **Wire CloudWatch alarms to PagerDuty/Slack** (the alarms exist; SNS
      topic subscription is intentionally left empty so an on-call rotation
      can be dropped in).
- [ ] **Onboard MFA factors** for any new admin operators in Cognito.
- [ ] **Bootstrap secrets in the target AWS account** before first
      `terraform apply` — copy `infra/terraform/envs/prod/terraform.tfvars.example`
      to `terraform.tfvars`, fill in partner-CA ARNs / Cognito callback URLs,
      and export `TF_VAR_hmac_secret_initial=$(openssl rand -base64 48)`.
      Full mapping of every Spring property → env var → AWS storage location
      (Secrets Manager / SSM / ECS env block) is in
      [`docs/SECRETS-INVENTORY.md`](SECRETS-INVENTORY.md).
- [ ] **Run gitleaks before every push** — pre-deploy gate is documented in
      `docs/SECRETS-INVENTORY.md` §6:
      `docker run --rm -v "$PWD":/r zricethezav/gitleaks:latest detect -s /r --no-git --redact`.

---

## 9. Useful one-liners

```bash
# Tail prod application logs (last 5 min, follow)
aws logs tail /ecs/tefca-gw-prod --since 5m --follow

# Force a fresh deploy of whatever :latest is in ECR
aws ecs update-service --cluster tefca-gw-prod --service tefca-gw-prod \
  --force-new-deployment

# Inspect the active task definition
aws ecs describe-services --cluster tefca-gw-prod --services tefca-gw-prod \
  --query 'services[0].taskDefinition'

# Check what endpoint URLs the routing layer currently sees for an org
psql "$DATABASE_URL" -c "SELECT endpoint_id, modality, url, active
                           FROM directory.directory_endpoints e
                           JOIN directory.directory_nodes n USING (node_id)
                          WHERE n.org_id = 'ORG-CHIT-PA-PLATFORM'
                          ORDER BY endpoint_id;"

# Force-flush the directory cache (use after any out-of-band SQL change)
curl -ksS -X POST "https://<alb>:8444/api/admin/proxy/directory/api/v1/admin/directory/cache/invalidate" \
  -H "Cookie: <admin session cookie>"
```

---

## 10. Where to look next

| If you need to… | Read |
| --------------- | ---- |
| Project overview, features, capability surface | [README.md](../README.md) |
| Single-page infra-team handoff index | [INFRA-HANDOFF.md](../INFRA-HANDOFF.md) |
| Spring property → env var → AWS storage mapping | [docs/SECRETS-INVENTORY.md](SECRETS-INVENTORY.md) |
| Cost-min architecture diagram + Terraform layout | [infra/README.md](../infra/README.md) |
| Onboard a new partner | This doc §5 ("Editing endpoint URLs") + add their cert to the ALB truststore |

