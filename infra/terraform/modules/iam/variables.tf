variable "name_prefix" {
  description = "Prefix for IAM roles and ECR repository names (e.g. tefca-gw-prod)."
  type        = string
}

variable "kms_cmk_arn" {
  description = "KMS CMK ARN used to encrypt ECR images at rest and grant CI decrypt access."
  type        = string
}

variable "tfstate_bucket" {
  description = "S3 bucket name holding Terraform remote state. The GitHub deploy role is granted read/write so CI can run terraform plan/apply."
  type        = string
  default     = "chit-terraform-state-sydata"
}

variable "github_org" {
  description = "GitHub organisation name for the OIDC trust policy subject claim."
  type        = string
}

variable "github_repo" {
  description = "GitHub repository name for the OIDC trust policy subject claim."
  type        = string
}

variable "github_branch" {
  description = "Git branch allowed to assume the GitHub deploy role."
  type        = string
  default     = "main"
}

variable "eks_cluster_name" {
  description = "EKS cluster name used to scope the IAM deploy-role policy without creating a Terraform module cycle."
  type        = string
}
