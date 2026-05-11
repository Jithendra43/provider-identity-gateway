# =============================================================================
# EKS — managed control plane + Fargate profiles
# Add-ons (AWS LB Controller, External Secrets) are installed post-apply via
# infra/eks/install-addons.sh (Helm) to avoid first-run Terraform/Helm cycles.
# =============================================================================

locals {
  access_github = {
    github_deploy = {
      principal_arn = var.github_deploy_role_arn
      policy_associations = {
        cluster_admin = {
          policy_arn = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"
          access_scope = {
            type = "cluster"
          }
        }
      }
    }
  }
}

data "aws_iam_policy_document" "fargate_cloudwatch_logs" {
  statement {
    sid    = "AllowCreateLogGroup"
    effect = "Allow"
    actions = [
      "logs:CreateLogGroup"
    ]
    resources = ["*"]
  }

  statement {
    sid    = "AllowWriteToEksLogGroups"
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogStreams"
    ]
    resources = ["arn:aws:logs:*:*:log-group:/aws/eks/*:*"]
  }
}

resource "aws_iam_policy" "fargate_cloudwatch_logs" {
  name_prefix = "${var.cluster_name}-fargate-cwlogs-"
  description = "CloudWatch Logs permissions for EKS Fargate pod execution role"
  policy      = data.aws_iam_policy_document.fargate_cloudwatch_logs.json
}

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.37"

  cluster_name    = var.cluster_name
  cluster_version = var.cluster_version

  vpc_id     = var.vpc_id
  subnet_ids = var.private_subnet_ids

  cluster_endpoint_public_access       = true
  cluster_endpoint_private_access      = true
  cluster_endpoint_public_access_cidrs = ["0.0.0.0/0"]

  cluster_encryption_config = {
    resources        = ["secrets"]
    provider_key_arn = var.kms_key_arn
  }

  enable_cluster_creator_admin_permissions = true
  access_entries                           = local.access_github

  fargate_profiles = {
    gateway = {
      name       = "gateway"
      subnet_ids = var.private_subnet_ids
      iam_role_additional_policies = {
        cloudwatch_logs = aws_iam_policy.fargate_cloudwatch_logs.arn
      }
      selectors = [
        {
          namespace = "tefca"
        },
        {
          namespace = "external-secrets"
        },
        {
          namespace = "kube-system"
        }
      ]
    }
  }

  tags = {
    Application = "tefca-gateway"
    Component   = "eks"
  }
}

# Fargate pod ENIs use the cluster primary security group. Allow ALB traffic to
# application ports exposed by ingress-auth-service and admin-ui workloads.
resource "aws_vpc_security_group_ingress_rule" "fargate_from_alb_8080" {
  security_group_id            = module.eks.cluster_primary_security_group_id
  description                  = "ALB to ingress-auth-service on :8080"
  referenced_security_group_id = var.alb_security_group_id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "fargate_from_alb_3000" {
  security_group_id            = module.eks.cluster_primary_security_group_id
  description                  = "ALB to admin-ui on :3000"
  referenced_security_group_id = var.alb_security_group_id
  from_port                    = 3000
  to_port                      = 3000
  ip_protocol                  = "tcp"
}
