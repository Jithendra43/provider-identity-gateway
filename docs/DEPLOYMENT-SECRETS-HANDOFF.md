# TEFCA Gateway — Deployment Secrets Handoff (Prod)

**To:** Infra / Platform team
**From:** Gateway dev
**Scope:** Everything that must exist in the AWS prod account **and** the GitHub repo before `terraform apply` + `deploy.yml` will succeed.
**Account / region:** `680780727322` / `us-east-1`
**Profile used by dev:** `tefca-rebuild`

> Convention: structure is in git, **values never are**. Sensitive values are written to AWS Secrets Manager / SSM SecureString or to GitHub Actions secrets. Templates are in [infra/terraform/envs/prod/terraform.tfvars.example](infra/terraform/envs/prod/terraform.tfvars.example) and [docs/SECRETS-INVENTORY.md](docs/SECRETS-INVENTORY.md).

---

## 1. GitHub Actions repo secrets (required for CI/CD)

Repo: `gauthamtota/esmd-tefca-gateway` → Settings → Secrets and variables → Actions.

| Secret name | Type | Used by | Notes / how to provision |
|---|---|---|---|
| `AWS_DEPLOY_ROLE_ARN` | string | `infra-plan.yml`, `infra-apply.yml`, `deploy.yml` | OIDC role created by `module.iam.aws_iam_role.github_deploy`. Format `arn:aws:iam::680780727322:role/tefca-gw-prod-github-deploy`. |
| `AWS_ACCOUNT_ID` | string | `backup-restore-drill.yml` | `680780727322` |
| `AWS_BACKUP_DRILL_ROLE_ARN` | string | `backup-restore-drill.yml` | Read-only role allowed to start RDS export-to-S3 + restore. **NEW — please create.** |
| `AWS_BACKUP_EXPORT_ROLE_ARN` | string | `backup-restore-drill.yml` | RDS export-task service role. **NEW — please create.** |
| `BACKUP_DRILL_BUCKET` | string | `backup-restore-drill.yml` | S3 bucket name for drill exports (encrypted, lifecycle-deleted in 7d). |
| `BACKUP_DRILL_KMS_KEY` | string | `backup-restore-drill.yml` | CMK ARN used to encrypt the export. Re-use `module.kms.cmk_arn`. |
| `STAGING_DB_REPLICA_HOST` | string | `backup-restore-drill.yml` | DNS of the restored Postgres instance the drill `psql`s into. |
| `STAGING_DB_USER` | string | `backup-restore-drill.yml` | App role on the restored instance (read-only). |
| `STAGING_DB_PASSWORD` | secret | `backup-restore-drill.yml` | Password for the above. Rotate quarterly. |
| `SECOPS_SLACK_WEBHOOK` | secret | `backup-restore-drill.yml` | Slack incoming-webhook URL for drill notifications. |
| `NVD_API_KEY` | secret | `ci.yml` (OWASP dep-check) | Free key from <https://nvd.nist.gov/developers/request-an-api-key>. Without it CI is rate-limited and slow. |
| `PROD_TFVARS` | secret (multiline) | `infra-plan.yml`, `infra-apply.yml` | **Full contents** of `infra/terraform/envs/prod/terraform.tfvars` (non-sensitive vars including `trusted_partner_cas` PEMs). Stored as a secret only because it includes partner CA names. |

---

## 2. Terraform sensitive variables (TF_VAR_* env vars at apply time)

Set as GitHub Actions secrets **and** referenced in `infra-apply.yml`. **Never** put these in `terraform.tfvars`.

| `TF_VAR_*` env var | Purpose | Generation command | Rotation |
|---|---|---|---|
| `TF_VAR_hmac_secret_initial` | Initial value of `tefca.hmac.secret` SSM SecureString. Used by gateway to sign internal HMAC tokens. | `openssl rand -base64 48` | 90 days — rotate via `aws ssm put-parameter --name /tefca-gw-prod/hmac/secret --type SecureString --overwrite --value "$(openssl rand -base64 48)"`. Currently auto-generated per apply by `infra-apply.yml` → consider pinning once stable. |

> The CI workflow currently **regenerates** `TF_VAR_hmac_secret_initial` on every apply (see [.github/workflows/infra-apply.yml#L63](.github/workflows/infra-apply.yml#L63)). Because the SSM `aws_ssm_parameter.hmac` uses `lifecycle { ignore_changes = [value] }` (verify), the regenerated value is harmless on re-apply. If not, please pin this to a one-time GitHub secret instead.

---

## 3. AWS Secrets Manager — Terraform-managed (no manual action)

Created automatically by `module.secrets`. Listed here so you can verify ARNs after first apply.

| AWS resource | AWS name | KMS key | Rotation | Consumed as |
|---|---|---|---|---|
| `aws_secretsmanager_secret.db` | `tefca-gw-prod/db` | `module.kms.cmk_arn` | RDS rotation Lambda, 7-day | ECS task envs `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (JSON fields `url`/`username`/`password`) |

---

## 4. AWS SSM Parameter Store — Terraform-managed

| Parameter name | Type | Source | Rotation | Consumed as |
|---|---|---|---|---|
| `/tefca-gw-prod/hmac/secret` | `SecureString` (CMK) | `TF_VAR_hmac_secret_initial` | 90d manual | `HMAC_SECRET` |
| `/tefca-gw-prod/cognito/client-secret` | `SecureString` (CMK) | Cognito user-pool client output | Manual via Cognito console then re-apply | `COGNITO_CLIENT_SECRET` |
| `/tefca-gw-prod/jwt/issuer-uri` | `String` | `var.jwt_issuer_uri` | n/a | `OAUTH2_ISSUER_URI`, `JWT_ISSUER_URI` |
| `/tefca-gw-prod/jwt/audience` | `String` | `var.jwt_audience` | n/a | `OAUTH2_AUDIENCE`, `JWT_AUDIENCE` |

---

## 5. Manual one-time operator actions

These cannot be Terraformed cleanly and need a human:

1. **DNS records** in Route53 (or external DNS) pointing `gateway.<your-domain>` and `admin-gateway.<your-domain>` at the ALB created by `module.alb`. After DNS is live, set `use_self_signed_server_cert = false` in `terraform.tfvars` and re-apply for a real ACM cert.
2. **Cognito admin users** — `aws cognito-idp admin-create-user --user-pool-id <id> --username ops@example.gov --user-attributes Name=email,Value=ops@example.gov ...`. Pool is admin-create-only. MFA enrolls on first login.
3. **Partner root CA bundles** — drop each PEM into the `trusted_partner_cas` map in `terraform.tfvars` (committed to GitHub secret `PROD_TFVARS`). One entry per onboarded QHIN.
4. **Email subscription confirmation** — if you set `partner_alert_email`, AWS sends a confirmation link to that address; a human must click it before SNS delivers alerts. **(NEW in this release.)**

---

## 6. NEW since last deploy — partner-onboarding + alerting feature

This release (`commit 0c3bd9c — feat(partners): admin onboarding/offboarding API + cert-expiry SNS alerts`) adds the following infra needs:

### 6a. New optional Terraform variable

| Variable | Sensitive | Default | Where to set |
|---|---|---|---|
| `partner_alert_email` | no | `""` (no subscription) | `terraform.tfvars` (i.e. `PROD_TFVARS` GH secret). Recommend `secops@<your-domain>` or a distribution list. |

### 6b. New AWS resources Terraform will create

| Resource | Notes |
|---|---|
| `aws_sns_topic.alerts` | `tefca-gw-prod-alerts`, KMS-encrypted with existing CMK. |
| `aws_sns_topic_policy.alerts` | Allows root account + `cloudwatch.amazonaws.com` to publish. |
| `aws_sns_topic_subscription.email` | Created **only** when `partner_alert_email` is set. Requires manual confirmation click. |
| `aws_iam_role_policy.task_alerts` | Inline `sns:Publish` on the ECS task role, gated on the topic existing. |

### 6c. New ECS task env var (Terraform-set, no secret)

| Env var | Value | Purpose |
|---|---|---|
| `TEFCA_ALERTS_SNS_TOPIC_ARN` | `module.alerting.topic_arn` | Activates `SnsAlertPublisher` + `CertificateExpiryScanner` beans. If empty/unset, both beans are skipped (`@ConditionalOnProperty`). |

### 6d. New schema migration

`database/migrations/V008__partner_endpoint_link.sql` — applied automatically by Flyway at task startup. Confirm the CloudWatch Logs line `Successfully applied 1 migration` after first deploy.

### 6e. New admin endpoints (auth: Cognito session, role `ROLE_QHIN_ADMIN`)

| Method | Path | Listener |
|---|---|---|
| `POST` | `/api/v1/admin/partners` | `:8444` (admin) |
| `DELETE` | `/api/v1/admin/partners/{partnerId}` | `:8444` (admin) |
| `GET` | `/api/v1/admin/partners[?status=]` | `:8444` (admin) |
| `GET` | `/api/v1/admin/partners/{partnerId}` | `:8444` (admin) |

No new public/partner endpoints. ALB rules unchanged.

---

## 7. Sanity checklist before `terraform apply`

- [ ] All GitHub secrets in §1 populated.
- [ ] `terraform.tfvars` (in `PROD_TFVARS`) has real `gateway_domain`, `admin_domain`, `github_org`, `github_repo`, and at least one `trusted_partner_cas` entry.
- [ ] `partner_alert_email` set to a monitored address (or intentionally left blank).
- [ ] CMK alias `alias/tefca-gw-prod-cmk` exists (created by `module.kms` on first apply).
- [ ] `terraform plan` shows: `+module.alerting.aws_sns_topic.alerts`, `+module.iam.aws_iam_role_policy.task_alerts[0]`, `~module.ecs` (env-var add only), `+aws_sns_topic_subscription.email[0]` (only if email set). Nothing else for the secrets stack.
- [ ] After apply: confirm SNS subscription email, then trigger a manual scan — `aws ecs execute-command` into a task and `curl localhost:8444/actuator/health` should be `UP` and CloudWatch should show `Cert expiry scan complete sent=N skippedDuplicate=0`.

---

## 8. Quick contact for handoff questions

- App/secret consumption: see [docs/SECRETS-INVENTORY.md](docs/SECRETS-INVENTORY.md) §2 mapping table.
- Terraform composition: [infra/terraform/envs/prod/main.tf](infra/terraform/envs/prod/main.tf).
- IAM policies: [infra/terraform/modules/iam/main.tf](infra/terraform/modules/iam/main.tf).
- Alerting module (new): [infra/terraform/modules/alerting/main.tf](infra/terraform/modules/alerting/main.tf).
