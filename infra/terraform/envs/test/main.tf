# =============================================================================
# TEFCA Gateway — test environment composition
# Cost target: ~$67/mo (vs. ~$95 baseline). See infra/COST.md.
# =============================================================================

terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Application = "tefca-gateway"
      Environment = "test"
      Compliance  = "HIPAA-TEFCA"
      ManagedBy   = "Terraform"
      CostCenter  = var.cost_center
    }
  }
}

locals {
  cluster_name = "${var.name_prefix}-test"
  vpc_name     = "${var.name_prefix}-test"
}

# =============================================================================
# VPC — created here (not inside modules/network) so that both modules/network
# and modules/securitygroups can receive vpc_id as an independent input,
# eliminating the Terraform module-reference cycle.
#
# Subnets, routing, IGW, and the NAT instance are in modules/network.
# Security groups (NAT, ALB, RDS) are in modules/securitygroups.
# =============================================================================

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags                 = { Name = local.vpc_name }
}

# VPC flow logs — HIPAA requirement (log all VPC traffic)
resource "aws_cloudwatch_log_group" "vpc_flow" {
  name              = "/aws/vpc/${local.vpc_name}/flow"
  retention_in_days = var.log_retention_days
  kms_key_id        = module.kms.cmk_arn
}

resource "aws_iam_role" "vpc_flow" {
  name = "${local.vpc_name}-flow-logs"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = { Service = "vpc-flow-logs.amazonaws.com" },
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "vpc_flow" {
  role = aws_iam_role.vpc_flow.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect   = "Allow",
      Action   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"],
      Resource = "${aws_cloudwatch_log_group.vpc_flow.arn}:*"
    }]
  })
}

resource "aws_flow_log" "main" {
  iam_role_arn    = aws_iam_role.vpc_flow.arn
  log_destination = aws_cloudwatch_log_group.vpc_flow.arn
  traffic_type    = "ALL"
  vpc_id          = aws_vpc.main.id
}

# -----------------------------------------------------------------------------
# Security Groups — all SGs consolidated in this module per infra-guidelines.
# Receives vpc_id from aws_vpc.main above (no module cycle).
# -----------------------------------------------------------------------------
module "securitygroups" {
  source      = "../../modules/securitygroups.tf"
  name_prefix = "${var.name_prefix}-test"
  vpc_id      = aws_vpc.main.id
  vpc_cidr    = aws_vpc.main.cidr_block
  # RDS ingress from EKS nodes is added via aws_vpc_security_group_ingress_rule
  # below after module.eks creates the node security group.
  allowed_rds_sg_ids = []
}

# -----------------------------------------------------------------------------
# Networking — subnets, IGW, route tables, NAT instance
# Receives vpc_id and nat_sg_id — no module cycle.
# -----------------------------------------------------------------------------
module "network" {
  source              = "../../modules/network"
  name                = "${var.name_prefix}-test"
  vpc_id              = aws_vpc.main.id
  vpc_cidr            = var.vpc_cidr
  availability_zones  = var.availability_zones
  enable_nat_instance = var.enable_nat_instance
  nat_instance_type   = var.nat_instance_type
  nat_sg_id           = module.securitygroups.nat_sg_id
  eks_cluster_name    = local.cluster_name
}

# -----------------------------------------------------------------------------
# Encryption — single CMK for S3, RDS, Secrets, SSM, CloudWatch Logs
# -----------------------------------------------------------------------------
module "kms" {
  source = "../../modules/kms"
  name   = "${var.name_prefix}-test"
}

# -----------------------------------------------------------------------------
# Private Certificate Authority — issues mTLS trust store + ALB server cert
# -----------------------------------------------------------------------------
module "acm" {
  source                      = "../../modules/acm"
  name                        = "${var.name_prefix}-test"
  domain_name                 = var.gateway_domain
  trust_store_bucket          = module.s3.trust_store_bucket
  partner_cas                 = var.trusted_partner_cas # PEM blobs of partner roots
  use_self_signed_server_cert = var.use_self_signed_server_cert
}

# -----------------------------------------------------------------------------
# S3 — audit (Object Lock COMPLIANCE 6yr), backups, alb-logs, trust-store
# -----------------------------------------------------------------------------
module "s3" {
  source               = "../../modules/s3"
  name_prefix          = "${var.name_prefix}-test"
  kms_key_arn          = module.kms.cmk_arn
  audit_retention_days = var.audit_retention_days
  alb_log_prefix       = var.alb_log_prefix
}

# -----------------------------------------------------------------------------
# RDS PostgreSQL — t4g.micro single-AZ, daily snapshots 35d, encrypted
# -----------------------------------------------------------------------------
module "rds" {
  source               = "../../modules/rds"
  name                 = "${var.name_prefix}-test"
  vpc_id               = module.network.vpc_id
  db_subnet_group_name = module.network.db_subnet_group_name
  primary_az           = var.availability_zones[0]
  # Postgres ingress from EKS nodes is aws_vpc_security_group_ingress_rule below
  # (avoids RDS ↔ secrets ↔ EKS cycle).
  allowed_sg_ids        = []
  kms_key_arn           = module.kms.cmk_arn
  instance_class        = var.rds_instance_class
  multi_az              = var.rds_multi_az
  backup_retention_days = var.rds_backup_retention_days
  deletion_protection   = var.rds_deletion_protection
  rds_sg_id             = module.securitygroups.rds_sg_id
}

# -----------------------------------------------------------------------------
# Redis (ElastiCache) — single-node cache in primary AZ, token auth enabled
# -----------------------------------------------------------------------------
module "redis" {
  source            = "../../modules/redis"
  name              = "${var.name_prefix}-test"
  kms_key_arn       = module.kms.cmk_arn
  subnet_group_name = module.network.cache_subnet_group_name
  security_group_id = module.securitygroups.redis_sg_id
  node_type         = var.redis_node_type
  primary_az        = var.availability_zones[0]
  auth_token        = module.secrets.redis_auth_token
}

# -----------------------------------------------------------------------------
# Secrets — DB pwd in Secrets Manager (rotated); other secrets in SSM
# SecureString (free; no rotation needed for HMAC/JWT pubkeys).
# -----------------------------------------------------------------------------
module "secrets" {
  source                = "../../modules/secrets"
  name_prefix           = "${var.name_prefix}-test"
  kms_key_arn           = module.kms.cmk_arn
  rds_username          = module.rds.username
  rds_password          = module.rds.password
  rds_endpoint          = module.rds.endpoint
  rds_db_name           = module.rds.db_name
  jwt_issuer_uri        = var.jwt_issuer_uri
  jwt_jwk_set_uri       = var.jwt_jwk_set_uri
  jwt_audience          = var.jwt_audience
  cognito_client_secret = module.cognito.client_secret
  # Redis primary endpoint — stored in SSM so gateway-ssm ExternalSecret can
  # expose REDIS_HOST to the pod without requiring a Terraform output in CI.
  redis_endpoint        = module.redis.address
}

# -----------------------------------------------------------------------------
# Cognito — TOTP MFA for /admin/** OAuth2 listener
# -----------------------------------------------------------------------------
module "cognito" {
  source = "../../modules/cognito"
  name   = "${var.name_prefix}-test-admin"
  callback_urls = [
    "https://${var.admin_domain}:8444/login/oauth2/code/cognito",
    "https://${var.admin_domain}/login/oauth2/code/cognito"
  ]
  logout_urls = [
    "https://${var.admin_domain}:8444/logout",
    "https://${var.admin_domain}/logout"
  ]
}

# -----------------------------------------------------------------------------
# ALB — :443 mTLS for partner traffic, :8444 OAuth2 for admin UI
# -----------------------------------------------------------------------------
module "alb" {
  source                   = "../../modules/alb"
  name                     = "${var.name_prefix}-test"
  vpc_id                   = module.network.vpc_id
  public_subnet_ids        = module.network.public_subnet_ids
  acm_server_cert_arn      = module.acm.server_cert_arn
  trust_store_arn          = module.acm.trust_store_arn
  access_logs_bucket       = module.s3.alb_logs_bucket
  enable_waf               = var.enable_waf
  cognito_user_pool_arn    = module.cognito.user_pool_arn
  cognito_client_id        = module.cognito.client_id
  cognito_user_pool_domain = module.cognito.domain
  admin_domain             = var.admin_domain
  gateway_domain           = var.gateway_domain
  alb_sg_id                = module.securitygroups.alb_sg_id
}

# -----------------------------------------------------------------------------
# Alerting — single SNS topic for partner cert expiry warnings + future
# CloudWatch alarms. Optional email subscription (set partner_alert_email).
# -----------------------------------------------------------------------------
module "alerting" {
  source      = "../../modules/alerting"
  name_prefix = "${var.name_prefix}-test"
  kms_key_arn = module.kms.cmk_arn
  alert_email = var.partner_alert_email
}

# -----------------------------------------------------------------------------
# IAM — ECR + GitHub OIDC deploy role (kubectl / helm from Actions)
# -----------------------------------------------------------------------------
module "iam" {
  source           = "../../modules/iam"
  name_prefix      = "${var.name_prefix}-test"
  kms_cmk_arn      = module.kms.cmk_arn
  github_org       = var.github_org
  github_repo      = var.github_repo
  github_branch    = var.github_branch
  eks_cluster_name = local.cluster_name
  tfstate_bucket   = var.tfstate_bucket
}

# -----------------------------------------------------------------------------
# EKS — managed Kubernetes + Fargate profiles; IRSA for app + add-on controllers
# Helm add-ons: run infra/eks/install-addons.sh once after the first apply.
# -----------------------------------------------------------------------------
module "eks" {
  source = "../../modules/eks"

  cluster_name           = local.cluster_name
  cluster_version        = var.eks_cluster_version
  aws_region             = var.aws_region
  vpc_id                 = module.network.vpc_id
  private_subnet_ids     = module.network.private_subnet_ids
  alb_security_group_id  = module.securitygroups.alb_sg_id
  github_deploy_role_arn = module.iam.github_deploy_role_arn
  kms_key_arn            = module.kms.cmk_arn
  name_prefix            = "${var.name_prefix}-test"

  audit_bucket_arn  = module.s3.audit_bucket_arn
  backup_bucket_arn = module.s3.backup_bucket_arn
  kms_cmk_arn       = module.kms.cmk_arn
  secret_arns       = module.secrets.all_arns
  alerts_topic_arn  = module.alerting.topic_arn

  depends_on = [module.iam, module.alb, module.secrets, module.alerting]
}

# RDS security group rule after EKS Fargate pod SG exists (see module.rds allowed_sg_ids = []).
resource "aws_vpc_security_group_ingress_rule" "rds_from_eks_fargate" {
  security_group_id            = module.rds.security_group_id
  description                  = "PostgreSQL from EKS Fargate pod ENIs"
  referenced_security_group_id = module.eks.fargate_pod_security_group_id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

# Redis security group rule after EKS Fargate pod SG exists.
resource "aws_vpc_security_group_ingress_rule" "redis_from_eks_fargate" {
  security_group_id            = module.securitygroups.redis_sg_id
  description                  = "Redis from EKS Fargate pod ENIs on :6379"
  referenced_security_group_id = module.eks.fargate_pod_security_group_id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}

# -----------------------------------------------------------------------------
# Observability — CloudWatch Logs (14d), no Firehose. App ships audit→S3.
# -----------------------------------------------------------------------------
module "observability" {
  source             = "../../modules/observability"
  name_prefix        = "${var.name_prefix}-test"
  kms_key_arn        = module.kms.cmk_arn
  log_retention_days = var.log_retention_days
}
