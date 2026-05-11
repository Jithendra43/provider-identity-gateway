# TEFCA Gateway — Prod Secrets to Provision (First Deployment)

**To:** Infra team
**Account:** 680780727322 / us-east-1
**Name prefix:** tefca-gw-prod
**Mode:** bootstrap — self-signed ALB cert, no real DNS, no backup-drill yet.

---

## 1. AWS Secrets Manager

```
# DB app credentials (Secrets Manager: tefca-gw-prod/db, JSON)
DB_USERNAME=tefca_app
DB_PASSWORD=ewOaELkxaE33wQm08R63jdH7RWMT
DB_NAME=tefca
DB_PORT=5432
DB_HOST=<rds-endpoint>          # fill after RDS create
DB_URL=jdbc:postgresql://<rds-endpoint>:5432/tefca?sslmode=require
```

## 2. AWS SSM Parameter Store (SecureString, encrypted with module.kms.cmk_arn)

```
# /tefca-gw-prod/hmac/secret
HMAC_SECRET=EC0gUcJ0NOXvh522pgH9kR7oPURb/vYlk1MBT/5bKzJKBhIxmfeoldY3Gcp4PJaE

# /tefca-gw-prod/cognito/client-secret
COGNITO_CLIENT_SECRET=<copy from Cognito user-pool app-client after create>

# /tefca-gw-prod/jwt/audience    (String, plain)
JWT_AUDIENCE=tefca-gateway
```

> `jwt/issuer-uri` will be set to the Cognito-generated issuer URL by Terraform on first apply — no manual value needed for the first deploy.

## 3. GitHub Actions repo secrets (gauthamtota/esmd-tefca-gateway → Settings → Secrets)

```
AWS_DEPLOY_ROLE_ARN=arn:aws:iam::680780727322:role/tefca-gw-prod-github-deploy
AWS_ACCOUNT_ID=680780727322
NVD_API_KEY=636d3798-48e7-433d-a732-a0b149c4f8dd
PROD_TFVARS=<full contents of infra/terraform/envs/prod/terraform.tfvars>
```

## 4. Terraform sensitive var (set as TF_VAR_* env in CI)

```
TF_VAR_hmac_secret_initial=EC0gUcJ0NOXvh522pgH9kR7oPURb/vYlk1MBT/5bKzJKBhIxmfeoldY3Gcp4PJaE
```
(same value as `HMAC_SECRET` above — Terraform writes it to SSM on first apply)

## 5. Terraform tfvars (non-sensitive, ship as PROD_TFVARS)

```hcl
aws_region   = "us-east-1"
name_prefix  = "tefca-gw"
cost_center  = "tefca-prod"

# Bootstrap mode — placeholder DNS until a real domain is wired in.
gateway_domain              = "gateway.tefca-gw-prod.internal"
admin_domain                = "admin-gateway.tefca-gw-prod.internal"
use_self_signed_server_cert = true

# Cognito-issued by default (Terraform fills in after Cognito module runs).
jwt_issuer_uri = ""        # leave empty for first apply
jwt_audience   = "tefca-gateway"

github_org  = "gauthamtota"
github_repo = "esmd-tefca-gateway"

# Optional: leave blank to skip the SNS subscription on first deploy.
partner_alert_email = ""

trusted_partner_cas = {
  # Add partner root CA PEMs here as partners onboard.
}
```

## 6. ECS task env vars (Terraform sets these — listed for reference only)

```
SPRING_PROFILES_ACTIVE=prod
AWS_REGION=us-east-1
TEFCA_ALERTS_SNS_TOPIC_ARN=<module.alerting.topic_arn>
DB_URL / DB_USERNAME / DB_PASSWORD       → from Secrets Manager
HMAC_SECRET / COGNITO_CLIENT_SECRET      → from SSM SecureString
```

---

### Post-apply manual steps

1. **Cognito admin user** (so you can log into `:8444`):
   ```
   aws cognito-idp admin-create-user \
     --user-pool-id <output: cognito_user_pool_id> \
     --username admin@example.com \
     --user-attributes Name=email,Value=admin@example.com Name=email_verified,Value=true \
     --temporary-password 'TempPass-ChangeMe-1!'
   ```
2. **Verify Flyway** ran 8 migrations in CloudWatch logs (look for `Successfully applied 8 migration`).
3. **Smoke test**: `curl -k https://<alb-dns>:8444/actuator/health` → `{"status":"UP"}`.

### Deferred to later deploys (skip for now)
- Real domain + ACM cert (flip `use_self_signed_server_cert = false`).
- Backup-restore drill (`AWS_BACKUP_DRILL_ROLE_ARN`, `BACKUP_DRILL_BUCKET`, `STAGING_DB_*`, `SECOPS_SLACK_WEBHOOK`).
- Partner SNS email subscription (`partner_alert_email`).
- External OIDC IdP for partner bearer tokens (`jwt_issuer_uri`).

### Rotation cadence
- `DB_PASSWORD` — RDS rotation Lambda, every 7 days (auto).
- `HMAC_SECRET` — every 90 days: `aws ssm put-parameter --name /tefca-gw-prod/hmac/secret --type SecureString --overwrite --value "$(openssl rand -base64 48)"`.
- `COGNITO_CLIENT_SECRET` — only on compromise; rotate via Cognito console + re-run Terraform.
