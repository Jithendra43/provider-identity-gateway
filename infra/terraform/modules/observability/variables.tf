variable "name_prefix" {
  description = "Prefix for CloudWatch log group and alarm names (e.g. tefca-gw-prod)."
  type        = string
}

variable "kms_key_arn" {
  description = "KMS key ARN for encrypting the CloudWatch log group."
  type        = string
}

variable "log_retention_days" {
  description = "Number of days to retain application logs in CloudWatch."
  type        = number
  default     = 14
}
