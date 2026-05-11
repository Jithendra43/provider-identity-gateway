# Terraform Infrastructure Template & Rules
## Based on: C-HIT Provider Identity Gateway (TEFCA / HIPAA-Compliant)

---

## 1. Folder Structure (Mandatory)

```
infra/terraform/
├── envs/
│   └── prod/               ← One folder per environment (prod, staging, dev)
│       ├── backend.tf      ← Remote state config (S3 + optional DynamoDB lock)
│       ├── main.tf         ← All module wiring for this environment
│       ├── variables.tf    ← Variable declarations (types + descriptions)
│       ├── terraform.tfvars← Non-sensitive default values (committed to git)
│       └── outputs.tf      ← Outputs exposed to operators / CI
└── modules/
    ├── network/            ← VPC subnets, route tables, IGW, NAT
    ├── securitygroups.tf/  ← ALL security groups live here (no exceptions)
    ├── kms/                ← Single CMK for all encryption
    ├── acm/                ← TLS certs + mTLS trust store
    ├── s3/                 ← Audit, backup, ALB-logs, trust-store buckets
    ├── rds/                ← PostgreSQL (or MySQL)
    ├── redis/              ← ElastiCache
    ├── secrets/            ← Secrets Manager + SSM SecureString
    ├── cognito/            ← User pool (admin auth)
    ├── alb/                ← Application Load Balancer + listeners
    ├── alerting/           ← SNS topic + CloudWatch alarms
    ├── iam/                ← ECR repo + GitHub OIDC deploy role
    ├── eks/                ← Managed Kubernetes + IRSA roles
    └── observability/      ← CloudWatch Log Groups
```

### Rule: Every module MUST contain
```
modules/<name>/
├── main.tf        ← resources
├── variables.tf   ← inputs
└── outputs.tf     ← outputs
```

---

## 2. Module Wiring Diagram (Dependency Order)

```
aws_vpc.main  (inline resource in envs/prod/main.tf)
     │
     ├──► module.kms          (no deps — create first)
     │
     ├──► module.securitygroups  (needs vpc_id)
     │         │
     │         └── outputs: nat_sg_id, alb_sg_id, rds_sg_id, redis_sg_id
     │
     ├──► module.network      (needs vpc_id, nat_sg_id)
     │         │
     │         └── outputs: public_subnet_ids, private_subnet_ids,
     │                       node_subnet_ids, db_subnet_group_name,
     │                       cache_subnet_group_name
     │
     ├──► module.s3           (needs kms_key_arn)
     │         └── outputs: audit_bucket_arn, alb_logs_bucket, trust_store_bucket
     │
     ├──► module.acm          (needs trust_store_bucket)
     │         └── outputs: server_cert_arn, trust_store_arn
     │
     ├──► module.rds          (needs db_subnet_group_name, kms_key_arn, rds_sg_id)
     │         └── outputs: endpoint, username, password, db_name, security_group_id
     │
     ├──► module.redis        (needs kms_key_arn, cache_subnet_group_name, redis_sg_id)
     │         └── outputs: endpoint, auth_token (via module.secrets)
     │
     ├──► module.secrets      (needs kms_key_arn, rds.username/password/endpoint)
     │         └── outputs: all_arns, redis_auth_token, db_secret_arn
     │
     ├──► module.cognito      (no AWS infra deps)
     │         └── outputs: user_pool_arn, client_id, client_secret, domain
     │
     ├──► module.alerting     (needs kms_key_arn)
     │         └── outputs: topic_arn
     │
     ├──► module.alb          (needs subnet_ids, acm, s3, cognito, securitygroups)
     │         └── outputs: alb_dns_name, alb_arn
     │
     ├──► module.iam          (needs kms_key_arn, github OIDC vars)
     │         └── outputs: github_deploy_role_arn, ecr_repository_url
     │
     └──► module.eks          (needs network, alb_sg, iam, kms, s3, secrets, alerting)
               └── outputs: cluster_name, cluster_endpoint, node_security_group_id,
                             oidc_provider_arn, irsa_*_role_arns

# After EKS is created, two standalone ingress rules unlock DB and cache:
aws_vpc_security_group_ingress_rule.rds_from_eks_nodes   (5432 from node SG)
aws_vpc_security_group_ingress_rule.redis_from_eks_nodes (6379 from node SG)
```

> **Why EKS security-group rules are separate resources and not inside modules?**
> RDS and Redis modules are created before EKS, so `module.eks.node_security_group_id`
> doesn't exist yet when those modules initialize. Adding the ingress rules as top-level
> resources after `module.eks` eliminates the circular dependency.

---

## 3. The VPC Anti-Cycle Pattern

The VPC is created as an **inline resource** in `envs/prod/main.tf`, NOT inside
`modules/network`. This is intentional:

```hcl
# envs/prod/main.tf
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
}

# Then pass vpc_id to both modules that need it independently:
module "securitygroups" {
  vpc_id = aws_vpc.main.id   # ← direct reference, no module cycle
}
module "network" {
  vpc_id    = aws_vpc.main.id
  nat_sg_id = module.securitygroups.nat_sg_id
}
```

**If VPC were inside `modules/network`**, then `modules/network` would have to output
`vpc_id`, and `modules/securitygroups` would reference `module.network.vpc_id`, but
`modules/network` also needs `nat_sg_id` from `modules/securitygroups` — **circular**.

---

## 4. Remote State (backend.tf)

```hcl
# envs/prod/backend.tf
terraform {
  backend "s3" {
    bucket  = "<your-state-bucket>"        # must be pre-created manually
    key     = "<project>/envs/prod/terraform.tfstate"
    region  = "us-east-1"
    encrypt = true
    # dynamodb_table = "<project>-tflock"  # optional but recommended for teams
    # kms_key_id     = "alias/<project>-tfstate"
  }
}
```

**Rules:**
- State bucket is created **outside Terraform** (bootstrap script) — never manage the
  state bucket with the same Terraform that stores state in it.
- Always set `encrypt = true`.
- Add DynamoDB lock table for any team environment to prevent concurrent applies.

---

## 5. Provider Block (main.tf)

```hcl
terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.70" }
    tls = { source = "hashicorp/tls", version = "~> 4.0" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Application = "<app-name>"
      Environment = "prod"
      Compliance  = "<compliance-label>"   # e.g. HIPAA, SOC2, PCI
      ManagedBy   = "Terraform"
      CostCenter  = var.cost_center
    }
  }
}
```

**Rule:** Always use `default_tags` in the provider block. Every resource inherits them
automatically — you only add resource-specific tags (e.g. `Name`) on individual resources.

---

## 6. Module Template (copy-paste starter)

### modules/\<name\>/variables.tf
```hcl
variable "name" {
  description = "Name prefix for all resources in this module."
  type        = string
}

variable "kms_key_arn" {
  description = "ARN of the KMS CMK used for encryption."
  type        = string
}

# Add module-specific variables below
```

### modules/\<name\>/main.tf
```hcl
# ---------------------------------------------------------------------------
# <Module Name>
# ---------------------------------------------------------------------------

locals {
  full_name = "${var.name}-<module-suffix>"
}

# resources go here
```

### modules/\<name\>/outputs.tf
```hcl
output "id" {
  description = "The ID of the primary resource."
  value       = aws_<resource>.<local_name>.id
}

output "arn" {
  description = "The ARN of the primary resource."
  value       = aws_<resource>.<local_name>.arn
}
```

---

## 7. Security Group Rules — The Single-Module Pattern

**All** security groups live in `modules/securitygroups.tf/main.tf`.
No module creates its own security group. Each resource module **receives** its SG id as
a variable.

```
modules/securitygroups.tf/
    main.tf    ← all aws_security_group resources
    variables.tf  ← vpc_id, vpc_cidr, name_prefix, allowed_rds_sg_ids
    outputs.tf    ← nat_sg_id, alb_sg_id, rds_sg_id, redis_sg_id, eks_additional_sg_id
```

**SG ingress rules that reference another module's SG (e.g. EKS node SG → RDS) are
standalone `aws_vpc_security_group_ingress_rule` resources in `envs/prod/main.tf`** —
not inside any module — because the source SG is created after the target SG.

---

## 8. Secrets Pattern

| Secret Type | Service | Rotation |
|---|---|---|
| DB password | AWS Secrets Manager | Lambda auto-rotation |
| HMAC key, JWT pubkeys | SSM Parameter Store (SecureString) | Manual via SSM |
| Redis auth token | Secrets Manager (referenced by ElastiCache) | Manual |
| Cognito client secret | Terraform output (→ SSM) | Manual |

```hcl
# modules/secrets/main.tf — pattern
resource "aws_secretsmanager_secret" "db" {
  name       = "/${var.name_prefix}/db-password"
  kms_key_id = var.kms_key_arn
}

resource "aws_ssm_parameter" "hmac_secret" {
  name   = "/${var.name_prefix}/hmac-secret"
  type   = "SecureString"
  value  = var.hmac_secret
  key_id = var.kms_key_arn
}
```

**Rule:** Never put sensitive values in `terraform.tfvars`. Supply via `TF_VAR_*`
environment variables in CI, or pass with `-var` flag.

---

## 9. HIPAA / Compliance Mandatory Checklist

| Requirement | Implementation |
|---|---|
| All data encrypted at rest | KMS CMK passed to every resource that accepts `kms_key_id` |
| All data encrypted in transit | RDS `tls = required`, Redis `in_transit_encryption_mode = required`, ALB TLS 1.2+ |
| VPC flow logs | `aws_flow_log.main` → CloudWatch Log Group encrypted with CMK |
| Audit logs immutable | S3 audit bucket with `object_lock_mode = COMPLIANCE`, 6yr retention |
| mTLS for partner traffic | ACM Private CA + ALB trust store |
| IAM least-privilege | IRSA roles per workload (EKS pods never share a node IAM role) |
| No long-lived credentials | GitHub Actions uses OIDC → assume-role (no AWS access keys) |
| Secrets rotation | Secrets Manager + Lambda rotation for DB password |
| Log retention | CloudWatch groups: 14 days app logs, 90 days VPC flow logs (adjust per policy) |

---

## 10. Variables Convention

```hcl
# variables.tf pattern
variable "rds_instance_class" {
  description = "RDS DB instance class."   # always provide
  type        = string                      # always explicit type
  default     = "db.t4g.micro"             # only if safe to default
}

variable "hmac_secret_initial" {
  description = "Initial HMAC secret; rotate via SSM thereafter."
  type        = string
  sensitive   = true    # hides value in plan/apply output
}
```

**Rules:**
1. Every `variable` block must have `description` and `type`.
2. Mark secrets `sensitive = true`.
3. All defaults live in `terraform.tfvars` — not in `variables.tf` — except truly
   universal defaults (region, Kubernetes version etc.).

---

## 11. Outputs Convention

```hcl
# envs/prod/outputs.tf pattern
output "rds_endpoint"      { value = module.rds.endpoint }
output "eks_cluster_name"  { value = module.eks.cluster_name }
output "kms_cmk_arn"       { value = module.kms.cmk_arn }
```

**Rules:**
- Expose all values that CI/CD pipelines or Kubernetes manifests need.
- Sensitive outputs (passwords) should NOT be in outputs — consume them via Secrets
  Manager / SSM in the application layer.

---

## 12. Module Reference Cheat-Sheet

| Module | Key Inputs | Key Outputs |
|---|---|---|
| `kms` | `name` | `cmk_arn` |
| `securitygroups` | `vpc_id`, `vpc_cidr`, `name_prefix` | `nat_sg_id`, `alb_sg_id`, `rds_sg_id`, `redis_sg_id` |
| `network` | `vpc_id`, `vpc_cidr`, `nat_sg_id`, `availability_zones` | `public_subnet_ids`, `private_subnet_ids`, `node_subnet_ids`, `db_subnet_group_name`, `cache_subnet_group_name` |
| `s3` | `name_prefix`, `kms_key_arn` | `audit_bucket_arn`, `alb_logs_bucket`, `trust_store_bucket` |
| `acm` | `name`, `domain_name`, `trust_store_bucket` | `server_cert_arn`, `trust_store_arn` |
| `rds` | `vpc_id`, `db_subnet_group_name`, `kms_key_arn`, `rds_sg_id` | `endpoint`, `username`, `password`, `security_group_id` |
| `redis` | `kms_key_arn`, `subnet_group_name`, `security_group_id`, `auth_token` | `endpoint` |
| `secrets` | `kms_key_arn`, `rds_*`, `hmac_secret` | `all_arns`, `redis_auth_token` |
| `cognito` | `name`, `callback_urls`, `logout_urls` | `user_pool_arn`, `client_id`, `client_secret`, `domain` |
| `alb` | `vpc_id`, `public_subnet_ids`, `acm_server_cert_arn`, `trust_store_arn`, `cognito_*`, `alb_sg_id` | `alb_dns_name` |
| `alerting` | `name_prefix`, `kms_key_arn` | `topic_arn` |
| `iam` | `name_prefix`, `kms_cmk_arn`, `github_org`, `github_repo`, `eks_cluster_name` | `github_deploy_role_arn`, `ecr_repository_url` |
| `eks` | `vpc_id`, `private_subnet_ids`, `alb_security_group_id`, `kms_key_arn`, `iam`, `secrets`, `alerting` | `cluster_name`, `cluster_endpoint`, `node_security_group_id`, `oidc_provider_arn`, `irsa_*_role_arns` |
| `observability` | `name_prefix`, `kms_key_arn`, `log_retention_days` | (CloudWatch log groups) |

---

## 13. Applying for a New Project — Step-by-Step

```bash
# 1. Create the state bucket (one-time, outside Terraform)
aws s3api create-bucket --bucket <project>-terraform-state --region us-east-1
aws s3api put-bucket-versioning --bucket <project>-terraform-state \
    --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket <project>-terraform-state \
    --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

# 2. Copy the env template
cp -r infra/terraform/envs/prod  infra/terraform/envs/<new-env>

# 3. Edit backend.tf  → update bucket key
# 4. Edit terraform.tfvars → update name_prefix, domains, region
# 5. Supply sensitive vars
export TF_VAR_hmac_secret_initial="$(openssl rand -base64 48)"

# 6. Init + plan + apply
terraform -chdir=infra/terraform/envs/<new-env> init
terraform -chdir=infra/terraform/envs/<new-env> plan -out=tfplan
terraform -chdir=infra/terraform/envs/<new-env> apply tfplan

# 7. After EKS is up: install Helm add-ons
bash infra/eks/install-addons.sh
```

---

## 14. The 6 Hard Rules (from infra-guidelines.md)

1. **Every module must have `variables.tf` and `outputs.tf`** — no exceptions.
2. **All defaults defined in `terraform.tfvars`** — not scattered in module variable blocks.
3. **Modules must be wired up in `envs/prod/main.tf`** — no resource creation inside a
   module that belongs at the env level.
4. **Security groups go to `modules/securitygroups.tf`** only — never inside rds, redis,
   eks, alb modules.
5. **Secrets (RDS, Redis, EKS) go to `modules/secrets`** — no secret values hard-coded
   anywhere else.
6. **No duplication** — if two modules need the same resource, the first creates it and
   outputs it; the second receives it as a variable.
