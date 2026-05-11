variable "name" {
  description = "Name prefix for ALB and related resources."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID where the ALB is deployed."
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnet IDs for the internet-facing ALB."
  type        = list(string)
}

variable "acm_server_cert_arn" {
  description = "ARN of the ACM certificate for ALB HTTPS listeners."
  type        = string
}

variable "trust_store_arn" {
  description = "ARN of the ALB mTLS trust store."
  type        = string
}

variable "access_logs_bucket" {
  description = "S3 bucket name for ALB access logs."
  type        = string
}

variable "enable_waf" {
  description = "Whether to attach a WAFv2 Web ACL to the ALB."
  type        = bool
  default     = true
}

variable "cognito_user_pool_arn" {
  description = "Cognito user pool ARN (used for admin OAuth2 listener metadata)."
  type        = string
}

variable "cognito_client_id" {
  description = "Cognito app client ID for the admin OAuth2 listener."
  type        = string
}

variable "cognito_user_pool_domain" {
  description = "Cognito hosted UI domain for the admin OAuth2 listener."
  type        = string
}

variable "admin_domain" {
  description = "DNS name for the Cognito-protected admin UI (:8444)."
  type        = string
}

variable "gateway_domain" {
  description = "DNS name for the partner mTLS gateway (:443)."
  type        = string
}

variable "alb_sg_id" {
  description = "Security group ID for the ALB, supplied by the securitygroups module."
  type        = string
}
