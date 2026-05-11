locals {
  buckets = {
    audit       = "${var.name_prefix}-audit"
    backup      = "${var.name_prefix}-backup"
    alb_logs    = "${var.name_prefix}-alb-logs"
    trust_store = "${var.name_prefix}-trust-store"
  }
}

resource "aws_s3_bucket" "this" {
  for_each            = local.buckets
  bucket              = each.value
  force_destroy       = false
  object_lock_enabled = each.key == "audit" # WORM only on audit
}

# ALB access logs require ACL-based writes from the ELB service account.
# Override the default BucketOwnerEnforced for the alb_logs bucket only.
resource "aws_s3_bucket_ownership_controls" "alb_logs" {
  bucket = aws_s3_bucket.this["alb_logs"].id
  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_versioning" "v" {
  for_each = aws_s3_bucket.this
  bucket   = each.value.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "enc" {
  for_each = aws_s3_bucket.this
  bucket   = each.value.id
  rule {
    apply_server_side_encryption_by_default {
      # ALB access logs only support SSE-S3 (AES256), not customer KMS.
      sse_algorithm     = each.key == "alb_logs" ? "AES256" : "aws:kms"
      kms_master_key_id = each.key == "alb_logs" ? null : var.kms_key_arn
    }
    bucket_key_enabled = each.key != "alb_logs"
  }
}

resource "aws_s3_bucket_public_access_block" "blk" {
  for_each                = aws_s3_bucket.this
  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# WORM 6-year retention on audit bucket — TEFCA + HIPAA
resource "aws_s3_bucket_object_lock_configuration" "audit_lock" {
  bucket = aws_s3_bucket.this["audit"].id
  rule {
    default_retention {
      mode = "COMPLIANCE"
      days = var.audit_retention_days
    }
  }
}

# ALB log delivery — uses the regional ELB AWS account principal (which writes
# with the bucket-owner-full-control ACL). Bucket ownership is set to
# BucketOwnerPreferred above so ACL-based writes are accepted.
data "aws_elb_service_account" "main" {}
data "aws_caller_identity" "current" {}

resource "aws_s3_bucket_policy" "alb_logs" {
  bucket = aws_s3_bucket.this["alb_logs"].id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Sid       = "ELBAccountPutLogs",
        Effect    = "Allow",
        Principal = { AWS = data.aws_elb_service_account.main.arn },
        Action    = "s3:PutObject",
        Resource  = "${aws_s3_bucket.this["alb_logs"].arn}/${var.alb_log_prefix}/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
      },
      {
        Sid       = "AWSLogDeliveryWrite",
        Effect    = "Allow",
        Principal = { Service = "delivery.logs.amazonaws.com" },
        Action    = "s3:PutObject",
        Resource  = "${aws_s3_bucket.this["alb_logs"].arn}/${var.alb_log_prefix}/AWSLogs/${data.aws_caller_identity.current.account_id}/*",
        Condition = {
          StringEquals = { "s3:x-amz-acl" = "bucket-owner-full-control" }
        }
      },
      {
        Sid       = "AWSLogDeliveryAclCheck",
        Effect    = "Allow",
        Principal = { Service = "delivery.logs.amazonaws.com" },
        Action    = "s3:GetBucketAcl",
        Resource  = aws_s3_bucket.this["alb_logs"].arn
      }
    ]
  })

  depends_on = [aws_s3_bucket_ownership_controls.alb_logs]
}

# Lifecycle — Glacier Deep Archive for audit > 1yr (heavy cost reduction)
resource "aws_s3_bucket_lifecycle_configuration" "audit_lc" {
  bucket = aws_s3_bucket.this["audit"].id
  rule {
    id     = "tier-to-deep-archive"
    status = "Enabled"
    filter {}
    transition {
      days          = 365
      storage_class = "DEEP_ARCHIVE"
    }
  }
}


