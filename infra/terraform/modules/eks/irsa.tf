# =============================================================================
# IRSA — AWS Load Balancer Controller, External Secrets, gateway application pod
# =============================================================================

resource "aws_iam_policy" "aws_load_balancer_controller" {
  name_prefix = "${var.cluster_name}-lbc-"
  description = "AWS Load Balancer Controller (upstream policy v2.8.2)"
  policy      = file("${path.module}/policies/aws-lbc-iam-policy.json")
}

module "irsa_lbc" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.39"

  role_name = "${var.cluster_name}-aws-lbc"

  role_policy_arns = {
    controller = aws_iam_policy.aws_load_balancer_controller.arn
  }

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-load-balancer-controller"]
    }
  }
}

resource "aws_iam_policy" "external_secrets" {
  name_prefix = "${var.cluster_name}-eso-"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Sid      = "ReadAppSecrets"
        Effect   = "Allow",
        Action   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret", "ssm:GetParameter", "ssm:GetParameters"],
        Resource = var.secret_arns
      },
      {
        Sid      = "KmsUse"
        Effect   = "Allow",
        Action   = ["kms:Decrypt", "kms:DescribeKey"],
        Resource = var.kms_cmk_arn
      }
    ]
  })
}

module "irsa_external_secrets" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.39"

  role_name = "${var.cluster_name}-external-secrets"

  role_policy_arns = {
    read = aws_iam_policy.external_secrets.arn
  }

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["external-secrets:external-secrets"]
    }
  }
}

resource "aws_iam_policy" "gateway_pod" {
  name_prefix = "${var.cluster_name}-gw-pod-"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = concat(
      [
        {
          Sid      = "AuditWrite"
          Effect   = "Allow",
          Action   = ["s3:PutObject", "s3:PutObjectRetention", "s3:GetObject", "s3:ListBucket"],
          Resource = [var.audit_bucket_arn, "${var.audit_bucket_arn}/*"]
        },
        {
          Sid      = "BackupRead"
          Effect   = "Allow",
          Action   = ["s3:GetObject", "s3:ListBucket"],
          Resource = [var.backup_bucket_arn, "${var.backup_bucket_arn}/*"]
        },
        {
          Sid      = "CmkUse"
          Effect   = "Allow",
          Action   = ["kms:GenerateDataKey", "kms:Decrypt", "kms:Encrypt"],
          Resource = var.kms_cmk_arn
        },
        {
          Sid      = "CloudWatchMetrics"
          Effect   = "Allow",
          Action   = ["cloudwatch:PutMetricData"],
          Resource = "*",
          Condition = {
            StringEquals = { "cloudwatch:namespace" = "tefca/prod" }
          }
        },
        {
          Sid      = "ReadRuntimeSecrets"
          Effect   = "Allow",
          Action   = ["secretsmanager:GetSecretValue", "ssm:GetParameter", "ssm:GetParameters"],
          Resource = var.secret_arns
        }
      ],
      var.alerts_topic_arn == "" ? [] : [{
        Sid      = "PublishAlerts"
        Effect   = "Allow",
        Action   = ["sns:Publish"],
        Resource = var.alerts_topic_arn
      }]
    )
  })
}

module "irsa_gateway_pod" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.39"

  role_name = "${var.cluster_name}-gateway-pod"

  role_policy_arns = {
    runtime = aws_iam_policy.gateway_pod.arn
  }

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["tefca:tefca-gateway"]
    }
  }
}
