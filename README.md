# C-HIT Provider Identity Gateway

Production-grade TEFCA (Trusted Exchange Framework and Common Agreement) Gateway
for secure healthcare data interoperability on AWS. The same gateway also
fronts non-TEFCA modalities — most notably the **Da Vinci Prior
Authorization (PA)** suite (CRD, DTR, PAS) — using the same policy engine,
mTLS edge, and audit pipeline.

## Features

- **Federated identity** — partner mTLS at the edge, OIDC/JWT for service
  callers, Cognito + MFA for human operators.
- **Provider directory** — Caffeine-cached organisation / node / endpoint
  registry with admin UI editor.
- **Prior authorisation routing** — Da Vinci CRD (6 hooks), DTR (5 ops),
  PAS (3 ops).
- **TEFCA core exchange** — patient discovery, document query / retrieve,
  message delivery on `/api/v1/tefca/**`.
- **Policy-as-code** — chain-of-responsibility policy engine
  (purpose-of-use → consent → org allow-list → ABAC).
- **Immutable audit** — S3 Object Lock COMPLIANCE 6-yr retention, KMS-CMK,
  one record per phase.
- **Observability** — CloudWatch dashboards + alarms, structured JSON logs,
  Spring Boot actuator health/metrics.

## Production deployment

Operators / infrastructure team — start here:

| Read | Purpose |
| ---- | ------- |
| [INFRA-HANDOFF.md](INFRA-HANDOFF.md) | One-page handoff index — GitHub secrets, AWS bootstrap, first-deploy runbook, verification |
| [docs/DEPLOYMENT-HANDOFF.md](docs/DEPLOYMENT-HANDOFF.md) | Deep deployment reference — ALB listener split, mTLS verification stages, manual deploy commands |
| [docs/SECRETS-INVENTORY.md](docs/SECRETS-INVENTORY.md) | Every Spring property → env var → AWS storage location, rotation cadence |
| [infra/README.md](infra/README.md) | Cost-min architecture diagram + Terraform layout + security posture |
| [docs/api/](docs/api/) | OpenAPI 3 specs (`openapi-ingress.yaml`, `openapi-policy.yaml`, `openapi-routing.yaml`, `openapi-directory.yaml`) |

## Architecture

The gateway is packaged as a **single fat-jar** (`tefca-gateway.jar`) running
in **one Fargate task** (cost-min prod profile). The four logical modules
(ingress-auth, policy, routing, directory-cache) load into the same Spring
context and call each other over `http://127.0.0.1:8080` (loopback) — zero
inter-service network egress, zero extra task cost.

| Module | Responsibility |
|--------|----------------|
| **ingress-auth** | External REST ingress, mTLS, JWT/OAuth2 validation, request orchestration |
| **policy** | TEFCA policy evaluation — exchange purpose, RBAC, delegation, obligations |
| **routing** | Route resolution — direct, fan-out, fallback, health-aware selection |
| **directory-cache** | Partner/org/node metadata cache (in-process Caffeine + PostgreSQL) |

Also bundled for self-contained smoke tests on a single task: a mock IdP
(`/oauth2/**`) and mock FHIR/XCA endpoints (`/mock-fhir/**`).

## Tech Stack

- **Java 21** / **Spring Boot 3.3**
- **PostgreSQL (RDS, single AZ, db.t4g.micro)** — primary data store
- **Caffeine (in-process)** — JWT replay + directory cache (replaces ElastiCache)
- **AWS ECS Fargate (ARM64 Graviton, 1 task)** — compute
- **NAT instance (t4g.nano)** — egress (replaces NAT GW)
- **Self-signed ACM** for ALB during bootstrap; mTLS partner trust store on ALB
- **Terraform** — infrastructure as code
- **GitHub Actions** — CI/CD

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose
- AWS CLI (for deployment)
- Terraform 1.7+ (for IaC)

## Quick Start

```bash
# Start local infrastructure (Postgres only — Caffeine is in-process)
docker-compose up -d

# Build all services
cd services
mvn clean verify

# Run a specific service
cd services/ingress-auth-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Project Structure

```
tefca-gateway/
├── services/                  # Java/Spring Boot microservices
│   ├── tefca-common/          # Shared library
│   ├── ingress-auth-service/  # Module 1: Ingress/Auth
│   ├── policy-service/        # Module 2: Policy
│   ├── routing-service/       # Module 3: Routing
│   └── directory-cache-service/ # Module 4: Directory Cache
├── database/                  # Flyway migrations & seed data
├── infrastructure/            # Terraform IaC
├── deploy/                    # ECS task/service definitions
├── docs/                      # Architecture docs, OpenAPI specs, runbooks
└── .github/workflows/         # CI/CD pipelines
```

## Service Ports (Local Development)

| Service | App Port | Actuator Port |
|---------|----------|---------------|
| Ingress/Auth | 8080 | 8080 |
| Policy | 8081 | 8081 |
| Routing | 8082 | 8082 |
| Directory Cache | 8083 | 8083 |
| PostgreSQL | 5432 | — |

## API Documentation

OpenAPI specs are in `docs/api/`:
- `openapi-ingress.yaml`
- `openapi-policy.yaml`
- `openapi-routing.yaml`
- `openapi-directory.yaml`

## Capabilities

The gateway exposes two parallel capability families behind one ALB,
one fat-jar, one policy engine, and one audit pipeline.

### TEFCA Core (`/api/v1/tefca/**`)

| Operation                | Modality       | Path                                      |
|--------------------------|----------------|-------------------------------------------|
| Patient Discovery (XCPD) | `XCPD`         | `POST /api/v1/tefca/patient-discovery`    |
| Document Query (XCA)     | `XCA_QUERY`    | `POST /api/v1/tefca/document-query`       |
| Document Retrieve (XCA)  | `XCA_RETRIEVE` | `POST /api/v1/tefca/document-retrieve`    |
| Direct Message Delivery  | `DIRECT`       | `POST /api/v1/tefca/message-delivery`     |
| FHIR Proxy (catch-all)   | `FHIR`         | `POST /api/v1/tefca/**`                   |

Auth: partner JWT (RS256) over mTLS. Bypassed only for `/admin/**` and
`/actuator/**`.

### Prior Authorization — Da Vinci suite (`/api/v1/pa/**`)

Gated by `tefca.pa.enabled=true` (default true). 14 endpoints across
three Da Vinci IGs.

**CRD — Coverage Requirements Discovery (CDS Hooks 2.0):**

| Hook                  | Modality                  | Path                                          |
|-----------------------|---------------------------|-----------------------------------------------|
| `order-sign`          | `PA_ORDER_SIGN`           | `POST /api/v1/pa/pa-crd`                      |
| `order-select`        | `PA_ORDER_SELECT`         | `POST /api/v1/pa/pa-crd-select`               |
| `appointment-book`    | `PA_APPOINTMENT_BOOK`     | `POST /api/v1/pa/pa-appointment`              |
| `order-dispatch`      | `PA_ORDER_DISPATCH`       | `POST /api/v1/pa/pa-order-dispatch`           |
| `encounter-start`     | `PA_ENCOUNTER_START`      | `POST /api/v1/pa/pa-encounter-start`          |
| `encounter-discharge` | `PA_ENCOUNTER_DISCHARGE`  | `POST /api/v1/pa/pa-encounter-discharge`      |

**DTR — Documentation Templates & Rules (FHIR R4):**

| Operation               | Modality                          | Path                                                |
|-------------------------|-----------------------------------|-----------------------------------------------------|
| Questionnaire Package   | `PA_DTR_QUESTIONNAIRE_PACKAGE`    | `POST /api/v1/pa/dtr/questionnaire-package`         |
| Questionnaire Read      | `PA_DTR_QUESTIONNAIRE_READ`       | `GET  /api/v1/pa/dtr/questionnaire/{id}`            |
| Library Read            | `PA_DTR_LIBRARY_READ`             | `GET  /api/v1/pa/dtr/library/{id}`                  |
| Response Submit         | `PA_DTR_RESPONSE_SUBMIT`          | `POST /api/v1/pa/dtr/questionnaire-response`        |
| Response Read           | `PA_DTR_RESPONSE_READ`            | `GET  /api/v1/pa/dtr/questionnaire-response/{id}`   |

**PAS — Prior Authorization Support (Da Vinci):**

| Operation              | Modality                 | Path                                          |
|------------------------|--------------------------|-----------------------------------------------|
| Claim Submit           | `PA_CLAIM_SUBMIT`        | `POST /api/v1/pa/pas/claim-submit`            |
| Claim Inquire          | `PA_CLAIM_INQUIRE`       | `POST /api/v1/pa/pas/claim-inquire`           |
| Claim Response Read    | `PA_CLAIM_RESPONSE_READ` | `GET  /api/v1/pa/pas/claim-response/{id}`     |

Auth: **partner mTLS only** (no JWT from partner). The gateway:

1. Validates the client cert against the partner trust store (`MtlsValidationFilter`).
2. Maps cert thumbprint → provider org via `MtlsOrgIdentityFilter` (Caffeine, 5-min TTL).
3. Mints a short-lived internal RS256 JWT (`InternalTokenIssuer`, issuer `tefca-gateway-internal`, 60 s TTL).
4. Routes via the same policy → routing → forward pipeline as TEFCA.
5. The dedicated `mockPaSecurityFilterChain` (`@Order(13)`) keeps the resource-server
   chain off `/mock-pa/**` so `MockPaController` can self-validate the internal JWT.

#### Smoke test (production ALB)

```bash
URL=https://tefca-gw-prod-554226928.us-east-1.elb.amazonaws.com
CERT=test-harness/certs/PART-EPIC-001.crt
KEY=test-harness/certs/PART-EPIC-001.key

curl -ksS --cert $CERT --key $KEY \
  -X POST "$URL/api/v1/pa/pa-crd" \
  -H 'Content-Type: application/json' \
  -d @test-harness/payloads/cds-order-sign.json
```

Expected: HTTP 200 with `status: SUCCESS`, three obligations
(`AUDIT_TRAIL_REQUIRED`, `MINIMUM_NECESSARY`, `PA_AUDIT_TRAIL`),
and a CDS Hooks `cards[]` array from the in-process Mock PA.

## Security Features

- **mTLS** — Client certificate validation for partner organizations
- **JWT/OAuth2** — Token-based authentication with domain-specific claim validation
- **JWT Replay Cache** — Validated tokens tracked in an in-process Caffeine cache (5-min TTL) to detect replay attacks
- **HMAC-SHA256 Internal Signing** — Service-to-service requests signed with HMAC for mutual authentication
- **Per-Partner Rate Limiting** — Database-driven rate limits per partner org (overrides global defaults)
- **Partner Trust Store** — Certificate thumbprint-based partner identification
- **PHI-Safe Logging** — Patient identifiers are hashed before logging; structured JSON logging in deployed environments

## Database Schemas

| Schema | Migration | Contents |
|--------|-----------|----------|
| `policy` | V003 | Policy rules, versions, audit |
| `directory` | V002 | Nodes, organizations, endpoints, capabilities |
| `routing` | V004 | Route executions, idempotency |
| `audit` | V005 | Audit events with patient_id_hash |
| `ingress` | V006 | Partners, certificates, OAuth config, per-partner rate limits |

## Policy Validators (Chain of Responsibility)

| Validator | Rule ID | Purpose |
|-----------|---------|---------|
| OperationValidator | POLICY-010 | Validates operation-modality compatibility |
| ExchangePurposeValidator | POLICY-001 | Checks allowed exchange purposes |
| RequesterOrgValidator | POLICY-002 | Verifies requester organization |
| ModalityValidator | POLICY-003 | Validates transport modality |
| PatientRequiredValidator | POLICY-004 | Ensures patient ID when required |
| RoleAuthorizationValidator | POLICY-005 | RBAC checks |
| DelegationRuleValidator | POLICY-006 | Delegation chain validation |
| ConsentValidator | POLICY-007 | Consent requirements per exchange purpose |
| TimeWindowValidator | POLICY-008 | Maintenance window enforcement |
| DataClassValidator | POLICY-009 | Data category scope limits |

## Routing Strategies

| Strategy | Priority | Use Case |
|----------|----------|----------|
| FanOutRouteStrategy | 0 | Broadcast to all endpoints (Patient Discovery) |
| DirectRouteStrategy | 1 | Single endpoint by modality (default) |
| FallbackRouteStrategy | 100 | Any active endpoint when modality mismatch |

## Production Hardening

- **Graceful Shutdown** — 30-second drain period on all services
- **HTTP/2** — Enabled on all services for multiplexed connections
- **dumb-init** — PID 1 signal proxying in all Docker containers
- **Structured JSON Logging** — Logstash-encoded JSON in dev/staging/prod profiles
- **Health Probes** — Kubernetes-compatible liveness/readiness at `/actuator/health`

## License

Proprietary — C-HIT
