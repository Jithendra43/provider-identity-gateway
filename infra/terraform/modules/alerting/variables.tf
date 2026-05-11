variable "name_prefix" {
  description = "Prefix for the SNS topic name (typically '<app>-<env>')."
  type        = string
}

variable "kms_key_arn" {
  description = "Customer-managed KMS key used to encrypt the SNS topic."
  type        = string
}

variable "alert_email" {
  description = "Optional email address subscribed to the alerts topic. Leave empty to provision the topic without a subscription (operators can subscribe ad-hoc later)."
  type        = string
  default     = ""
}
