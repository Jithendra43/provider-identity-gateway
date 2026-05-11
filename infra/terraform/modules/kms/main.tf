resource "aws_kms_key" "this" {
  description             = "${var.name} CMK — S3, RDS, Secrets, SSM, Logs"
  deletion_window_in_days = 30
  enable_key_rotation     = true # HIPAA: annual rotation
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Sid       = "EnableRoot",
        Effect    = "Allow",
        Principal = { AWS = "arn:aws:iam::${data.aws_caller_identity.me.account_id}:root" },
        Action    = "kms:*",
        Resource  = "*"
      },
      {
        Sid       = "AllowCloudWatchLogs",
        Effect    = "Allow",
        Principal = { Service = "logs.${data.aws_region.here.name}.amazonaws.com" },
        Action    = ["kms:Encrypt*", "kms:Decrypt*", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:Describe*"],
        Resource  = "*",
        Condition = {
          ArnLike = { "kms:EncryptionContext:aws:logs:arn" = "arn:aws:logs:${data.aws_region.here.name}:${data.aws_caller_identity.me.account_id}:log-group:*" }
        }
      }
    ]
  })
}

resource "aws_kms_alias" "this" {
  name          = "alias/${var.name}"
  target_key_id = aws_kms_key.this.id
}

data "aws_caller_identity" "me" {}
data "aws_region" "here" {}


