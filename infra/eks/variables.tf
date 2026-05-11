variable "aws_region" {
  description = "AWS region where the EKS cluster is deployed."
  type        = string
  default     = "us-east-1"
}

variable "cluster_name" {
  description = "EKS cluster name. Used only when the infra remote state is unavailable."
  type        = string
  default     = ""
}

variable "lbc_role_arn" {
  description = "IRSA role ARN for the AWS Load Balancer Controller. Used only when the infra remote state is unavailable."
  type        = string
  default     = ""
}

variable "external_secrets_role_arn" {
  description = "IRSA role ARN for the External Secrets Operator. Used only when the infra remote state is unavailable."
  type        = string
  default     = ""
}

variable "vpc_id" {
  description = "VPC ID where the EKS cluster runs. Used only when the infra remote state is unavailable."
  type        = string
  default     = ""
}
