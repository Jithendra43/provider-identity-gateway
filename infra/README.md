# C-HIT Provider Identity Gateway — infrastructure overview

DevOps and Terraform implementation details are documented in [infra/terraform/DEVOPS-TERRAFORM-HANDOFF.md](terraform/DEVOPS-TERRAFORM-HANDOFF.md).

This repository uses a Terraform-first AWS deployment model with three infrastructure layers under [infra](.):

- [infra/terraform](terraform): base platform infrastructure (network, EKS cluster, ALB, ACM, RDS, IAM/IRSA, Route53, Cognito, secrets wiring)
- [infra/eks](eks): EKS add-on stack managed by Terraform (AWS Load Balancer Controller, External Secrets, CoreDNS Fargate patch, Fargate logging config)
- [infra/k8s](k8s): workload Kubernetes manifests applied during deploy (namespace, service account, external secrets, service, target group bindings, deployment)

## Current architecture

```text
Internet
   |
   |- ALB :443  (mTLS verify, partner ingress)
   |- ALB :8444 (HTTPS admin UI, Cognito OIDC)
   |
   v
EKS Fargate profile (no EC2 node groups)
   |- tefca-gateway Deployment (linux/amd64 image)
   |- TargetGroupBinding -> existing ALB target groups
   |
   |- RDS PostgreSQL
   |- S3 audit bucket
   |- Secrets Manager + SSM via External Secrets Operator
```

Important listener behavior:

- Port 443 is mTLS-protected by design and will reject normal browser sessions without a trusted client certificate.
- Port 8444 is the browser/admin endpoint and should be used for admin sign-in flows.

## Folder responsibilities

### [infra/terraform](terraform)

Base AWS stack:

- VPC, subnets, security groups
- EKS cluster and Fargate profile
- ALB listeners and target groups
- ACM certificates and trust store
- Cognito user pool, client, and hosted UI domain
- RDS, S3, IAM, KMS, Route53, alerting

Primary entrypoint:

- [infra/terraform/envs/prod/main.tf](terraform/envs/prod/main.tf)

### [infra/eks](eks)

Terraform-managed cluster add-ons:

- AWS Load Balancer Controller (Helm)
- External Secrets Operator (Helm)
- CoreDNS scheduling patch for Fargate-only clusters
- Fargate logging namespace/configmap

Primary entrypoint:

- [infra/eks/main.tf](eks/main.tf)

### [infra/k8s](k8s)

Runtime Kubernetes resources used by deployment workflow:

- Namespace and service account
- ClusterSecretStore and ExternalSecret resources
- Service and TargetGroupBinding resources
- tefca-gateway Deployment manifest template

Key files:

- [infra/k8s/00-namespace.yaml](k8s/00-namespace.yaml)
- [infra/k8s/01-serviceaccount.yaml](k8s/01-serviceaccount.yaml)
- [infra/k8s/02-clustersecretstores.yaml](k8s/02-clustersecretstores.yaml)
- [infra/k8s/03-externalsecret-db.yaml](k8s/03-externalsecret-db.yaml)
- [infra/k8s/04-externalsecret-ssm.yaml](k8s/04-externalsecret-ssm.yaml)
- [infra/k8s/05-service.yaml](k8s/05-service.yaml)
- [infra/k8s/06-targetgroupbindings.yaml](k8s/06-targetgroupbindings.yaml)
- [infra/k8s/07-deployment.yaml](k8s/07-deployment.yaml)

## Apply order

1. Bootstrap Terraform backend once with [infra/bootstrap.sh](bootstrap.sh).
2. Apply base platform in [infra/terraform/envs/prod](terraform/envs/prod).
3. Apply add-ons in [infra/eks](eks).
4. Run deploy pipeline to apply [infra/k8s](k8s) manifests and roll workload image.

## CI/CD workflows

- [ .github/workflows/infra-plan.yml ](../.github/workflows/infra-plan.yml): terraform plan for base + add-ons
- [ .github/workflows/infra-apply.yml ](../.github/workflows/infra-apply.yml): terraform apply for base + add-ons
- [ .github/workflows/deploy.yml ](../.github/workflows/deploy.yml): image build/push and Kubernetes deployment

## Operational notes

- If browser sign-in reports redirect mismatch, verify Cognito callback/logout URLs include both host-only and host:8444 values.
- If External Secrets fail with DNS errors, verify CoreDNS rollout in kube-system and Fargate compute-type patch state.
- If pods show image exec format errors, verify runtime image and Docker build are linux/amd64.
