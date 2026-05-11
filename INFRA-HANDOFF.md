# C-HIT Provider Identity Gateway — Infrastructure Handoff

> Single-page index for the infrastructure team. Everything needed to take
> this repository, point it at a fresh AWS account, and run it in production.

---

## 1. Where to start

| Read | Purpose |
| ---- | ------- |
| [README.md](README.md) | What the application does, capability surface, tech stack |
| [docs/DEPLOYMENT-HANDOFF.md](docs/DEPLOYMENT-HANDOFF.md) | Deep deployment reference: ALB listener split, mTLS verification stages, request flow, manual-deploy commands |
| [docs/SECRETS-INVENTORY.md](docs/SECRETS-INVENTORY.md) | Every Spring property → env var → AWS storage location, who supplies it, rotation cadence |
| [infra/README.md](infra/README.md) | Cost-min architecture diagram, Terraform layout, security posture |
| [infra/terraform/envs/prod/terraform.tfvars.example](infra/terraform/envs/prod/terraform.tfvars.example) | Template for the per-environment variable file |

---

## 2. Required GitHub repository secrets

The five GitHub Actions workflows under `.github/workflows/` (`ci.yml`,
`deploy.yml`, `infra-plan.yml`, `infra-apply.yml`, `backup-restore-drill.yml`)
authenticate to AWS via **OIDC** (no long-lived keys). Configure these
under **Settings → Secrets and variables → Actions**:

| Secret name | Used by | Value |
| ----------- | ------- | ----- |
| `AWS_DEPLOY_ROLE_ARN` | `deploy.yml`, `infra-apply.yml`, `infra-plan.yml`, `backup-restore-drill.yml` | The OIDC deploy role created by Terraform module `iam` — default name `<name_prefix>-gh-deploy` (current prod value: `arn:aws:iam::680780727322:role/tefca-gw-prod-gh-deploy`). Find yours with `terraform -chdir=infra/terraform/envs/prod output -raw github_deploy_role_arn` or `aws iam get-role --role-name tefca-gw-prod-gh-deploy --query Role.Arn --output text`. |
| `NVD_API_KEY` *(optional but recommended)* | `ci.yml` security-scan | NVD API key from <https://nvd.nist.gov/developers/request-an-api-key>. Without it, the OWASP Dependency-Check NVD download is rate-limited and occasionally corrupts its H2 cache mid-build. |

`AWS_DEPLOY_ROLE_ARN` is the only **required** secret. Everything else is
either a non-sensitive workflow env var (already in the workflow files) or
read from AWS Secrets Manager / SSM SecureString at task start by the
application itself.

### GitHub environments (manual approval gates)

`deploy.yml` and `infra-apply.yml` both target the GitHub environment named
**`prod`**. Configure it under **Settings → Environments → prod**:

- Required reviewers: at least one infra-team member.
- Wait timer: optional (e.g. 5 min).
- Deployment branches: `main` only.

---

## 3. One-time AWS bootstrap

Before the very first `terraform apply`, perform the following in the target
AWS account (these resources are intentionally outside Terraform because
Terraform itself depends on them).

### 3.1 GitHub OIDC provider + deploy role

```bash
# 3.1.a  Create the GitHub OIDC provider (one per AWS account, lives forever)
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

```bash
# 3.1.b  Create the deploy role (trust policy below; permissions = AdministratorAccess
#         scoped to this account, OR a custom policy containing ecs:*, ecr:*,
#         iam:PassRole, s3:*, dynamodb:*, kms:*, secretsmanager:*, ssm:*,
#         logs:*, cognito-idp:*, ec2:*, elasticloadbalancing:*, rds:*, acm:*,
#         route53:*, cloudwatch:*, application-autoscaling:* — see
#         infra/terraform/modules/iam/main.tf for the operational task role
#         which is narrower)
cat > /tmp/trust.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
      "StringLike":   { "token.actions.githubusercontent.com:sub": "repo:<GITHUB_ORG>/<GITHUB_REPO>:ref:refs/heads/main" }
    }
  }]
}
EOF
aws iam create-role --role-name tefca-gw-github-actions --assume-role-policy-document file:///tmp/trust.json
aws iam attach-role-policy --role-name tefca-gw-github-actions --policy-arn arn:aws:iam::aws:policy/AdministratorAccess
```

Record the resulting role ARN → set as the GitHub secret `AWS_DEPLOY_ROLE_ARN`.

### 3.2 Terraform state backend

```bash
cd /path/to/this/repo
export AWS_DEFAULT_REGION=us-east-1
export AWS_PAGER=
./infra/bootstrap.sh
```

This script is idempotent and creates:

- S3 bucket `tefca-gw-tfstate-<ACCOUNT_ID>` (versioned, SSE-AES256, all-public-blocked).
- DynamoDB table `tefca-gw-tflock` (PAY_PER_REQUEST).
- Writes `infra/terraform/envs/prod/backend.hcl` (gitignored).
- Prints a fresh **HMAC bootstrap secret** to stdout — **save it in your password manager**, you will need it in step 4.

### 3.3 ACM certificates and Route53

Create (or import) ACM certificates in **us-east-1** for `gateway_domain`
and `admin_domain`. The Terraform `acm` module can do DNS validation if a
public Route53 hosted zone for the parent domain exists in the same account.
For an imported third-party cert, set `use_self_signed_server_cert = false`
and pre-import the cert ARN before `terraform apply`.

For the bootstrap apply with no real domain yet, leave the variable
`use_self_signed_server_cert = true` in `terraform.tfvars`.

---

## 4. First deploy runbook

```bash
# 4.1  Configure prod tfvars
cd infra/terraform/envs/prod
cp terraform.tfvars.example terraform.tfvars
$EDITOR terraform.tfvars      # fill: gateway_domain, admin_domain,
                              #       trusted_partner_cas, github_org,
                              #       github_repo, jwt_issuer_uri

# 4.2  Export the bootstrap HMAC secret from step 3.2
export TF_VAR_hmac_secret_initial='<paste from password manager>'

# 4.3  Init & apply
terraform init -backend-config=backend.hcl
terraform plan  -out=tfplan
terraform apply tfplan
```

After apply succeeds the ECS service `tefca-gw-prod` will be created with
`desiredCount=1` but **no image in ECR yet** — the task will fail to start.
Push the first image:

```bash
# 4.4  First image push (CI/CD does this on every subsequent commit)
gh workflow run deploy.yml --ref main
# OR manually: see docs/DEPLOYMENT-HANDOFF.md §4 "Application deploy"
```

ECS will converge to `runningCount=1` once the image lands.

### 4.5 Cognito admin user (one per operator)

The user pool denies self-signup. Create operators by hand:

```bash
USER_POOL=$(aws cognito-idp list-user-pools --max-results 50 \
  --query "UserPools[?starts_with(Name, 'tefca-gw-prod')].Id" --output text)
aws cognito-idp admin-create-user --user-pool-id "$USER_POOL" \
  --username operator@your-org.gov \
  --user-attributes Name=email,Value=operator@your-org.gov Name=email_verified,Value=true \
  --desired-delivery-mediums EMAIL
```

The operator receives a one-time password by email and is forced to set up
TOTP MFA on first login.

---

## 5. Variables to fill in

Real values for these go in `infra/terraform/envs/prod/terraform.tfvars`
(non-sensitive) or `TF_VAR_*` env vars (sensitive). Template:
[terraform.tfvars.example](infra/terraform/envs/prod/terraform.tfvars.example).

| Variable | Sensitive | Source |
| -------- | --------- | ------ |
| `aws_region` | no | Account region (default `us-east-1`) |
| `name_prefix` | no | Default `tefca-gw` |
| `cost_center` | no | Tag value, default `tefca-prod` |
| `gateway_domain` | no | Public FQDN for partner mTLS endpoint (e.g. `gateway.qhin.example.gov`) |
| `admin_domain` | no | Public FQDN for the operator admin UI (e.g. `admin-gateway.qhin.example.gov`) |
| `image_tag` | no | ECR tag; the deploy workflow injects `${git_sha}` per release |
| `trusted_partner_cas` | no | `{ partner_alias = file("certs/partner-roots/<root>.pem") }` per onboarded QHIN |
| `hmac_secret_initial` | **YES** | `export TF_VAR_hmac_secret_initial="$(openssl rand -base64 48)"` (or value from `bootstrap.sh`) |
| `jwt_issuer_uri` | no | OIDC issuer for partner bearer tokens |
| `jwt_audience` | no | Default `tefca-gateway` |
| `github_org`, `github_repo` | no | For the OIDC trust on the GitHub-Actions role |
| `use_self_signed_server_cert` | no | `true` only for the very first bootstrap with no real DNS; flip to `false` once ACM + Route53 are wired |

Per-Spring-property mapping (DB password, Cognito client secret, etc.) is
in [docs/SECRETS-INVENTORY.md §2](docs/SECRETS-INVENTORY.md).

---

## 6. Verification

After the first deploy converges to steady state:

```bash
ALB=$(cd infra/terraform/envs/prod && terraform output -raw alb_dns_name)

# Admin endpoint — unauth should redirect to /admin/welcome/
curl -skI "https://${ALB}:8444/admin/dashboard/" | grep -iE 'http/|location'
# → HTTP/2 302  /admin/welcome/

# Welcome landing page — public, returns the C-HIT branded HTML
curl -sk "https://${ALB}:8444/admin/welcome/" | grep -oE 'C-HIT Provider'
# → C-HIT Provider

# Health check
curl -sk "https://${ALB}:8444/actuator/health" | head -c 200

# Partner endpoint (will fail without a partner cert — expected)
curl -skI "https://${ALB}/api/v1/tefca/patient-discovery"
# → curl error / connection rejected at mTLS handshake (correct)
```

Smoke an actual partner request with the included test certificates:

```bash
curl -ksS \
  --cert test-harness/certs/PART-EPIC-001.crt \
  --key  test-harness/certs/PART-EPIC-001.key \
  -H 'Content-Type: application/json' \
  -d @test-harness/payloads/cds-order-sign.json \
  -X POST "https://${ALB}/api/v1/pa/pa-crd"
# → HTTP 200 with status SUCCESS, three obligations, CDS Hooks cards[]
```

---

## 7. Known follow-ups for the infra team

1. **C-HIT logo placeholder.** [`apps/admin-ui/public/chit-logo.png`](apps/admin-ui/public/chit-logo.png) and [`infra/cognito-ui/logo.png`](infra/cognito-ui/logo.png) are placeholder color-wheel badges. Drop the official C-HIT mark at both paths and re-run `deploy.yml` for the SPA + the AWS console step `aws cognito-idp set-ui-customization` for the Cognito Hosted UI.
2. **Real ACM cert + Route53.** Bootstrap currently uses a self-signed cert. Issue a public ACM cert for `gateway_domain` and `admin_domain`, validate via DNS, then set `use_self_signed_server_cert = false` and re-apply.
3. **Partner truststore.** `trusted_partner_cas` starts empty. Add one entry per onboarded QHIN root CA before the first partner traffic.
4. **Cognito email verification.** SES is not provisioned by Terraform. If admin-user creation must send branded emails, configure SES (verified domain or sandbox identity) and update the user pool's `EmailConfiguration`.
5. **Branch protection on `main`.** Recommended: require a passing `ci.yml` run + at least one PR review before merge. Repository → Settings → Branches → Add rule.

---

## 8. Repository layout (one-line tour)

```
tefca-gateway/
├── README.md                  Project overview, features, smoke test
├── INFRA-HANDOFF.md           This file
├── Makefile                   Local dev driver (make up / e2e / postman-mtls)
├── docker-compose.yml         Local dev stack
├── .env.example               Local dev env template
├── .github/workflows/         5 workflows (ci, deploy, infra-plan/-apply, dr-drill)
├── apps/admin-ui/             Next.js 14 admin SPA (statically exported at build)
├── services/                  Java 21 / Spring Boot 3.3 (Maven multi-module)
│   ├── tefca-gateway-app/     The deployable fat-jar + Dockerfile
│   ├── ingress-auth-service/  Public ingress, mTLS, JWT, admin proxy
│   ├── policy-service/        Policy chain
│   ├── routing-service/       Endpoint resolver + downstream forwarder
│   ├── directory-cache-service/ Org / endpoint registry
│   └── tefca-common/          Shared models
├── database/                  Local Postgres init / Flyway sources
├── infra/
│   ├── README.md              Architecture + deploy walkthrough
│   ├── bootstrap.sh           One-off Terraform state backend
│   ├── cognito-ui/            Hosted-UI CSS + logo
│   └── terraform/
│       ├── envs/prod/         The only deployable composition
│       └── modules/           11 reusable modules (network, alb, ecs, rds,
│                              s3, kms, iam, cognito, secrets, acm,
│                              observability)
├── test-harness/              docker-compose mocks, Postman collections,
│                              partner test certs, payload fixtures
├── docs/
│   ├── DEPLOYMENT-HANDOFF.md  Deep deployment reference
│   └── SECRETS-INVENTORY.md   Spring → env → AWS mapping
└── config/                    Build helpers (checkstyle, spotbugs, OWASP)
```
