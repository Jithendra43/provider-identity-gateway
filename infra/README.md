# C-HIT Provider Identity Gateway — production AWS deployment

**DevOps / Terraform map:** see [`infra/terraform/DEVOPS-TERRAFORM-HANDOFF.md`](terraform/DEVOPS-TERRAFORM-HANDOFF.md) (modules, `envs/prod` wiring, apply order).

Cost-aware **Amazon EKS** (Graviton managed node group) behind the existing **ALB**
(mTLS + admin listener). Pods register to the same ALB target groups via the
**AWS Load Balancer Controller** (`TargetGroupBinding`).

## Architecture

```
Internet
   │
   ├── ALB :443  (mTLS verify, partner trust store) ──┐
   └── ALB :8444 (Cognito OIDC, MFA-on)               │
                                                      ▼
                              ┌─────────────────────────────────────────┐
                              │  EKS — Deployment (ARM64 image)         │
                              │  ─ TefcaGatewayApplication.jar           │
                              │     (same fat-jar layout as before)      │
                              │  TargetGroupBinding → existing ALB TGs   │
                              └────────┬───────────────┬────────────────┘
                                       │               │
                                  RDS pg t4g.micro    S3 audit (WORM 6yr)
                                  Single-AZ           SSE-KMS, Glacier@1y
```

## Deployment workflow (phased)

1. **Bootstrap state backend (one-off, idempotent)**:
   ```bash
   AWS_ACCESS_KEY_ID=...  AWS_SECRET_ACCESS_KEY=...  AWS_DEFAULT_REGION=us-east-1 \
       ./infra/bootstrap.sh
   ```
   Creates `tefca-gw-tfstate-<accountId>` (versioned, SSE-AES256, all-public-blocked),
   the `tefca-gw-tflock` DynamoDB table, writes `infra/terraform/envs/prod/backend.hcl`,
   and prints a fresh HMAC secret. Save the secret in your password manager.
2. **First-time secrets**: `export TF_VAR_hmac_secret_initial=<value-from-step-1>`
3. **Terraform (VPC, ALB, RDS, EKS cluster, IRSA, …)**:
   ```bash
   cd infra/terraform/envs/prod
   cp terraform.tfvars.example terraform.tfvars   # edit domains, github org/repo
   terraform init -backend-config=backend.hcl
   terraform plan -out=tfplan
   terraform apply tfplan
   ```
   Note `terraform output` for `eks_irsa_*_role_arn`, `vpc_id`, and `eks_cluster_name`.
4. **Phase — Helm add-ons (once per cluster)** after the cluster is `ACTIVE`:
   ```bash
   export CLUSTER_NAME=tefca-gw-prod
   export AWS_REGION=us-east-1
   export VPC_ID=$(terraform output -raw vpc_id)
   export LBC_ROLE_ARN=$(terraform output -raw eks_irsa_lbc_role_arn)
   export ESO_ROLE_ARN=$(terraform output -raw eks_irsa_external_secrets_role_arn)
   chmod +x ../../../eks/install-addons.sh
   ../../../eks/install-addons.sh
   ```
5. **Phase — Kubernetes manifests**: GitHub Actions `deploy.yml` applies `infra/k8s/`
   (namespace, IRSA `ServiceAccount`, `ClusterSecretStore`, `ExternalSecret`, `Deployment`,
   `Service`, `TargetGroupBinding`) and rolls the image. First pipeline run needs a
   pushed image (build step in the same workflow).

If your Terraform `name_prefix` / cluster name is **not** `tefca-gw-prod`, update
`NAME_PREFIX` / `EKS_CLUSTER` in `.github/workflows/deploy.yml` and the defaults in
`infra/k8s/*.yaml` accordingly.

## Security posture (TEFCA + HIPAA)

- **mTLS partner ingress**: ALB native mutual auth, trust store rejects expired client certs. App still validates `X-Amzn-Mtls-Clientcert` thumbprint against DB whitelist.
- **JWT (RS256)**: Validated against partner JWKS; `tefca-gateway` audience pinned.
- **HMAC service-to-service**: Enforced even on loopback; if we ever scale to >1 task, no code change needed.
- **Encryption at rest**: KMS CMK with annual rotation on RDS, S3, Secrets, SSM, CloudWatch Logs.
- **Encryption in transit**: TLS 1.2 minimum (`ELBSecurityPolicy-TLS13-1-2-2021-06`); `rds.force_ssl=1`.
- **Audit immutability**: S3 Object Lock COMPLIANCE 6 yr (TEFCA + HIPAA).
- **Least privilege**: Gateway **IRSA** role can `s3:PutObject` only to audit bucket; GitHub deploy role is scoped to ECR, EKS describe, ELB describe TG, SSM JWT params, and Cognito read for CD.
- **Network isolation**: Nodes and RDS in private subnets; egress via VPC endpoints + single NAT instance.
- **Admin MFA**: Cognito TOTP enforced; 60-minute access tokens.
- **WAF**: AWS managed Common + KnownBadInputs + 2000-RPM rate limit.
- **Container hardening**: Distroless nonroot where used in the image; drop Linux capabilities in Kubernetes `securityContext` as you tighten the chart.

## Future toggles (one-line flips when needs grow)

| Need                          | Change                                        |
|-------------------------------|-----------------------------------------------|
| HA database                   | `rds.multi_az = true`                         |
| Multi-AZ NAT                  | `network.enable_nat_instance = false` (uses managed NAT GW) |
| Distributed cache             | Add `module "elasticache"` and set `tefca.replay.backend=redis` |
| Decompose to microservices    | Add Deployments/Services in EKS; point `tefca.services.*-url` at cluster DNS or internal ALB |
| FIPS 140-3                    | Switch base image to `eclipse-temurin:21-jdk` + BouncyCastle FIPS provider |
