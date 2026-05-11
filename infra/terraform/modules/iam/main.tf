data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  account_id       = data.aws_caller_identity.current.account_id
  region           = data.aws_region.current.name
  eks_cluster_arn  = "arn:aws:eks:${local.region}:${local.account_id}:cluster/${var.eks_cluster_name}"
  gateway_pod_role = "arn:aws:iam::${local.account_id}:role/${var.eks_cluster_name}-gateway-pod"
  # SSM paths match modules/secrets (name_prefix = e.g. tefca-gw-prod)
  jwt_ssm_arn_prefix      = "arn:aws:ssm:${local.region}:${local.account_id}:parameter/${var.name_prefix}/jwt/"
  secrets_mgr_arn_prefix  = "arn:aws:secretsmanager:${local.region}:${local.account_id}:secret:${var.name_prefix}/*"
  tfstate_bucket_arn      = "arn:aws:s3:::${var.tfstate_bucket}"
  github_oidc_provider_arn = "arn:aws:iam::${local.account_id}:oidc-provider/token.actions.githubusercontent.com"
}

# -----------------------------------------------------------------------------
# ECR repo (immutable tags, scan on push)
# -----------------------------------------------------------------------------
resource "aws_ecr_repository" "app" {
  name                 = "${var.name_prefix}/gateway"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = var.kms_cmk_arn
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1,
      description  = "Keep last 30 images",
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 30 },
      action       = { type = "expire" }
    }]
  })
}

# -----------------------------------------------------------------------------
# GitHub OIDC role — ECR push + kubectl/helm (EKS) + read-only AWS for deploy scripts
# NOTE: The OIDC provider (token.actions.githubusercontent.com) is already
# provisioned in this AWS account. We reference it via a data source instead
# of creating it again to avoid a Terraform conflict error.
# -----------------------------------------------------------------------------

# data source — resolves the pre-existing GitHub OIDC provider ARN
data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

resource "aws_iam_role" "github_deploy" {
  name = "${var.name_prefix}-gh-deploy"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect    = "Allow",
      Principal = { Federated = local.github_oidc_provider_arn },
      Action    = "sts:AssumeRoleWithWebIdentity",
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        },
        StringLike = {
          "token.actions.githubusercontent.com:sub" = [
            "repo:${var.github_org}/${var.github_repo}:ref:refs/heads/${var.github_branch}",
            "repo:${var.github_org}/${var.github_repo}:environment:prod",
          ]
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "github_deploy" {
  role = aws_iam_role.github_deploy.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      # ------------------------------------------------------------------
      # ECR — build and push container images
      # ------------------------------------------------------------------
      {
        Sid    = "EcrAuth",
        Effect = "Allow",
        Action = ["ecr:GetAuthorizationToken"],
        Resource = "*"
      },
      {
        Sid    = "EcrPush",
        Effect = "Allow",
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:PutImage",
          "ecr:DescribeRepositories",
          "ecr:ListImages",
        ],
        Resource = aws_ecr_repository.app.arn
      },
      # ------------------------------------------------------------------
      # EKS — resolve cluster endpoint for kubectl / helm
      # ------------------------------------------------------------------
      {
        Sid      = "EksDescribe",
        Effect   = "Allow",
        Action   = ["eks:DescribeCluster"],
        Resource = local.eks_cluster_arn
      },
      {
        Sid      = "EksList",
        Effect   = "Allow",
        Action   = ["eks:ListClusters"],
        Resource = "*"
      },
      # ------------------------------------------------------------------
      # Kubernetes API — used by CI kubectl / helm steps via token from EKS
      # The actual K8s RBAC grant is the access entry in modules/eks.
      # ------------------------------------------------------------------
      {
        Sid      = "EksAccessEntry",
        Effect   = "Allow",
        Action   = [
          "eks:DescribeAccessEntry",
          "eks:ListAccessEntries",
        ],
        Resource = local.eks_cluster_arn
      },
      # ------------------------------------------------------------------
      # ALB / target groups — health checks, TargetGroupBinding verification
      # ------------------------------------------------------------------
      {
        Sid    = "ElbReadForDeploy",
        Effect = "Allow",
        Action = [
          "elasticloadbalancing:DescribeTargetGroups",
          "elasticloadbalancing:DescribeTargetHealth",
          "elasticloadbalancing:DescribeLoadBalancers",
          "elasticloadbalancing:DescribeListeners",
        ],
        Resource = "*"
      },
      # ------------------------------------------------------------------
      # Terraform remote state — read plan, write state (CI tf apply)
      # ------------------------------------------------------------------
      {
        Sid    = "TfStateReadWrite",
        Effect = "Allow",
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket",
        ],
        Resource = [
          local.tfstate_bucket_arn,
          "${local.tfstate_bucket_arn}/*",
        ]
      },
      # ------------------------------------------------------------------
      # Secrets Manager — read deploy-time config (DB endpoint, JWT keys)
      # ------------------------------------------------------------------
      {
        Sid    = "SecretsReadForDeploy",
        Effect = "Allow",
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret",
        ],
        Resource = local.secrets_mgr_arn_prefix
      },
      # ------------------------------------------------------------------
      # SSM — read JWT / HMAC parameters baked into ECS / K8s secrets
      # ------------------------------------------------------------------
      {
        Sid    = "SsmJwtParams",
        Effect = "Allow",
        Action = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"],
        Resource = "${local.jwt_ssm_arn_prefix}*"
      },
      # ------------------------------------------------------------------
      # KMS — allow CI to decrypt state / secrets with the shared CMK
      # ------------------------------------------------------------------
      {
        Sid    = "KmsDecryptForDeploy",
        Effect = "Allow",
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey",
          "kms:DescribeKey",
        ],
        Resource = var.kms_cmk_arn
      },
      # ------------------------------------------------------------------
      # Cognito — read pool / client details for deploy-time config
      # ------------------------------------------------------------------
      {
        Sid    = "CognitoReadForDeploy",
        Effect = "Allow",
        Action = [
          "cognito-idp:ListUserPools",
          "cognito-idp:DescribeUserPool",
          "cognito-idp:ListUserPoolClients",
          "cognito-idp:DescribeUserPoolClient",
        ],
        Resource = "*"
      },
      # ------------------------------------------------------------------
      # IAM — gate-check on the gateway pod IRSA role (no changes)
      # ------------------------------------------------------------------
      {
        Sid      = "IamGetGatewayPodRole",
        Effect   = "Allow",
        Action   = ["iam:GetRole"],
        Resource = local.gateway_pod_role
      },
      # ------------------------------------------------------------------
      # SNS / S3 list — lightweight introspection during deploy
      # ------------------------------------------------------------------
      {
        Sid    = "SnsListForDeploy",
        Effect = "Allow",
        Action = ["sns:ListTopics"],
        Resource = "*"
      },
      {
        Sid    = "S3ListBucketsForDeploy",
        Effect = "Allow",
        Action = ["s3:ListAllMyBuckets"],
        Resource = "*"
      }
    ]
  })
}


