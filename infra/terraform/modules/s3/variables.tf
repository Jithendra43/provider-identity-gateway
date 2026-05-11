variable "name_prefix" {
  description = "Prefix for all S3 bucket names (e.g. tefca-gw-prod)."
  type        = string
}

variable "kms_key_arn" {
  description = "KMS key ARN for server-side encryption on non-ALB-log buckets."
  type        = string
}

variable "audit_retention_days" {
  description = "Object Lock COMPLIANCE retention period in years for the audit bucket (TEFCA + HIPAA requirement)."
  type        = number
  default     = 6
}

variable "alb_log_prefix" {
  description = "S3 key prefix for ALB access logs; must match the prefix set on the aws_lb resource."
  type        = string
  default     = "alb"
}
