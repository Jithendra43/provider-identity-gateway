# TEFCA Gateway — Production Secrets & Env Inventory

This document is the single source of truth your AWS architects need to populate the prod account before `terraform apply`. Every Spring property the gateway reads from the environment is listed here, paired with where its value lives at runtime (AWS Secrets Manager / SSM Parameter Store / plain ECS env block) and who supplies it.

> **Convention:** templates and structure live in git. **Real secret values never live in git.** They are written into AWS Secrets Manager or SSM SecureString once during account bootstrap and referenced by ARN from the ECS task definition's `secrets:` block. The task execution role already has `secretsmanager:GetSecretValue` and `ssm:GetParameters`/`GetParameter` (see `infra/terraform/modules/iam/main.tf`).

---

## 1. Quick bootstrap checklist

For a fresh AWS account, the operator runs (in order):

1. `cp infra/terraform/envs/prod/terraform.tfvars.example infra/terraform/envs/prod/terraform.tfvars`, fill placeholders below.
2. Generate and export the initial HMAC secret out-of-band:
   ```sh
   export TF_VAR_hmac_secret_initial="$(openssl rand -base64 48)"
   ```
3. `cd infra/terraform/envs/prod && terraform init && terraform apply`. Terraform creates the Cognito user pool/client, RDS, secrets, and ECS task definition wired with ARN references.
4. Operator manually adds Cognito admin users (admin-create-only is enabled). MFA is enrolled on first login.
5. CD pipeline pushes a new image tag; subsequent `terraform apply` (or `aws ecs update-service`) rolls it out.

---

## 2. Spring property → env var → AWS storage

| Spring property | Env var | Storage at runtime | AWS resource (Terraform) | Who supplies | Rotation |
|---|---|---|---|---|---|
| `spring.datasource.url` | `DB_URL` | Secrets Manager (JSON field `url`) | `aws_secretsmanager_secret.db` (`{prefix}/db`) | Terraform/RDS auto-generated | Auto (RDS rotation Lambda, 7d) |
| `spring.datasource.username` | `DB_USERNAME` | Secrets Manager (JSON field `username`) | same | Terraform/RDS | Auto |
| `spring.datasource.password` | `DB_PASSWORD` | Secrets Manager (JSON field `password`) | same | Terraform/RDS | Auto |
| `tefca.hmac.secret` | `HMAC_SECRET` | SSM SecureString | `aws_ssm_parameter.hmac` (`{prefix}/hmac/secret`) | Operator (`TF_VAR_hmac_secret_initial`, then rotate via `aws ssm put-parameter`) | Manual (90 days recommended) |
| `spring.security.oauth2.client.registration.cognito.client-secret` | `COGNITO_CLIENT_SECRET` | SSM SecureString | `aws_ssm_parameter.cognito_client_secret` (`{prefix}/cognito/client-secret`) | Cognito generates; Terraform copies to SSM | Manual (rotate via Cognito console + re-run TF) |
| `spring.security.oauth2.client.provider.cognito.issuer-uri` | `COGNITO_ISSUER_URI` | SSM String | `aws_ssm_parameter.cognito_issuer` | Terraform (derived from pool) | N/A |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `OAUTH2_ISSUER_URI` | SSM String | `aws_ssm_parameter.jwt_issuer` | Operator (`var.jwt_issuer_uri`) | N/A |
| `spring.security.oauth2.resourceserver.jwt.audiences` | `OAUTH2_AUDIENCE` | ECS env block | n/a | `var.jwt_audience` (default `tefca-gateway`) | N/A |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | `OAUTH2_JWK_SET_URI` | ECS env block | derived from issuer | Computed in TF | N/A |
| `tefca.jwt.issuer-uri` | `JWT_ISSUER_URI` | SSM String | `aws_ssm_parameter.jwt_issuer` (shared) | Operator | N/A |
| `tefca.jwt.audience` | `JWT_AUDIENCE` | ECS env block | n/a | `var.jwt_audience` | N/A |
| `spring.security.oauth2.client.registration.cognito.client-id` | `COGNITO_CLIENT_ID` | ECS env block | n/a | Terraform output of Cognito client | N/A |
| `tefca.admin.cognito-issuer` | `COGNITO_ISSUER_URI` | ECS env block | (re-uses SSM value above) | Terraform | N/A |
| `tefca.admin.cognito-audience` | `COGNITO_AUDIENCE` | ECS env block | n/a | `= COGNITO_CLIENT_ID` | N/A |
| `tefca.audit.s3-bucket` | `AUDIT_BUCKET` | ECS env block | `aws_s3_bucket.audit` | Terraform | N/A |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | Dockerfile default | n/a | `prod` (baked) | N/A |
| `spring.flyway.enabled` | `FLYWAY_ENABLED` | ECS env block | n/a | `true` (default) | N/A |
| `tefca.mock-pa.enabled` | `TEFCA_MOCK_PA_ENABLED` | ECS env block | n/a | `false` in prod | N/A |
| `logging.level.org.springframework.security` | `SECURITY_LOG_LEVEL` | ECS env block | n/a | `WARN` (default) | N/A |

**Not used in prod** (local-dev / docker-compose only, do **not** set in prod ECS):
`POSTGRES_HOST/PORT/DB/USER/PASSWORD`, `REDIS_HOST/PORT`, `TEFCA_MTLS_KEYSTORE_*`, `ADMIN_AUTH_MODE`, `ADMIN_PASSWORD`, `OPERATOR_PASSWORD`, `AUDITOR_PASSWORD` — admin auth is Cognito Hosted UI in prod (`adminChain`), not the mock-form login.

---

## 3. Terraform variables your architects must supply

Defined in `infra/terraform/envs/prod/variables.tf`. Values go into `terraform.tfvars` (non-sensitive) or `TF_VAR_*` env vars (sensitive).

| Variable | Sensitive | Where it comes from |
|---|---|---|
| `aws_region` | no | Account region (default `us-east-1`) |
| `name_prefix` | no | Resource prefix (default `tefca-gw`) |
| `gateway_domain` | no | Public DNS for partner mTLS endpoint, e.g. `gateway.qhin.example.gov` |
| `admin_domain` | no | Public DNS for Cognito admin UI, e.g. `admin-gateway.qhin.example.gov` |
| `image_tag` | no | ECR tag (CD pipeline injects) |
| `trusted_partner_cas` | no | `{ partner_alias = file("certs/root.pem") }` for ALB truststore |
| `hmac_secret_initial` | **YES** | `export TF_VAR_hmac_secret_initial=$(openssl rand -base64 48)` |
| `jwt_issuer_uri` | no | OIDC issuer for partner-side bearer tokens |
| `jwt_audience` | no | Default `tefca-gateway` |
| `github_org`, `github_repo` | no | For OIDC trust to GitHub Actions (CD) |
| `use_self_signed_server_cert` | no | `false` once a real ACM cert is in place |

---

## 4. ALB listener split (already enforced in Terraform)

| Listener | TLS mode | Audience | mTLS verified |
|---|---|---|---|
| `:443`  | mutual TLS (ALB truststore) | **Partner / public** | ALB edge **+** in-process `MtlsValidationFilter` (defense-in-depth) |
| `:8444` | TLS-only + Cognito session  | **Operators / admin** | n/a (Cognito JWT) |

Architects must never expose `:8444` to partners and never expose `:443` to operators. Both are fronted by the same ALB; routing is by listener port, not host header.

---

## 5. Image promotion model

1. Build artifacts (jar + container image) are produced from `services/tefca-gateway-app/Dockerfile`, build context = repo root.
2. CD pushes to ECR `${account}.dkr.ecr.${region}.amazonaws.com/{name_prefix}-prod/gateway:{tag}`.
3. ECS task definitions **pin image tags** (not `:latest`); a new tag = a new task definition revision = `aws ecs update-service --task-definition`. `--force-new-deployment` alone is a no-op when the tag is unchanged.
4. Flyway runs at task startup; new SQL migrations under `database/migrations/V*.sql` apply automatically (watch for `Successfully applied N migration` in CloudWatch Logs).

---

## 6. Pre-deploy gitleaks gate

Before every `git push` from the build host:
```sh
docker run --rm -v "$PWD":/r zricethezav/gitleaks:latest detect -s /r --no-git --redact
```
Repo policy: **zero findings** on `main`. The committed `.env.example` and `terraform.tfvars.example` use placeholder strings only and are gitleaks-clean.
