variable "cluster_name" {
  type        = string
  description = "EKS cluster name (must match network subnet tags kubernetes.io/cluster/<name>)."
}

variable "cluster_version" {
  type        = string
  description = "Kubernetes version for the control plane."
  default     = "1.35"
}

variable "aws_region" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "Private subnets for control plane ENIs (all AZs)."
}

variable "alb_security_group_id" {
  type        = string
  description = "Internet-facing ALB security group; Fargate pods accept :8080/:3000 from here."
}

variable "github_deploy_role_arn" {
  type        = string
  description = "GitHub Actions OIDC deploy role — granted EKS API cluster-admin via access entry."
}

variable "kms_key_arn" {
  type        = string
  description = "KMS CMK for envelope encryption of Kubernetes secrets at rest."
}

variable "name_prefix" {
  type        = string
  description = "Prefix matching secrets/S3 naming (e.g. tefca-gw-prod)."
}

variable "audit_bucket_arn" {
  type = string
}

variable "backup_bucket_arn" {
  type = string
}

variable "kms_cmk_arn" {
  type        = string
  description = "Shared app CMK (S3/RDS); used in pod IRSA policies."
}

variable "secret_arns" {
  type        = list(string)
  description = "Secrets Manager + SSM ARNs the gateway pod may read."
}

variable "alerts_topic_arn" {
  type        = string
  default     = ""
  description = "SNS topic for certificate expiry alerts (optional)."
}

