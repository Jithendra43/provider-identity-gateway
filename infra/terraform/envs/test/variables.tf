variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "name_prefix" {
  type    = string
  default = "tefca-gw"
}

variable "cost_center" {
  type    = string
  default = "tefca-prod"
}

variable "gateway_domain" {
  description = "Public DNS for partner mTLS endpoint, e.g. gateway.example.gov"
  type        = string
}

variable "admin_domain" {
  description = "Public DNS for Cognito-protected admin UI"
  type        = string
}

variable "image_tag" {
  description = "ECR image tag to deploy (set by CD pipeline)"
  type        = string
  default     = "latest"
}

variable "trusted_partner_cas" {
  description = "Map of partner_name => PEM root CA contents for ALB mTLS trust store"
  type        = map(string)
  default     = {}
}

variable "jwt_issuer_uri" {
  type = string
}

variable "jwt_jwk_set_uri" {
  description = "JWKS endpoint URL (e.g., https://idp.example.gov/.well-known/jwks.json). Ask your IdP admin or find it at <issuer>/.well-known/openid-configuration under 'jwks_uri'. Falls back to mock IdP in bootstrap mode."
  type        = string
  default     = "http://localhost:8080/oauth2/.well-known/jwks.json"
}

variable "jwt_audience" {
  type    = string
  default = "tefca-gateway"
}

variable "github_org" {
  type = string
}

variable "github_repo" {
  type    = string
  default = "tefca-gateway"
}

variable "use_self_signed_server_cert" {
  type        = bool
  default     = false
  description = "If true, ALB uses a self-signed cert imported to ACM (bootstrap mode). Set to false once a real domain + DNS validation is in place."
}

variable "partner_alert_email" {
  type        = string
  default     = ""
  description = "Optional ops email subscribed to the alerts SNS topic. AWS will send a confirmation link before alerts are delivered. Leave empty to provision the topic with no subscription."
}

# ─── Networking ───────────────────────────────────────────────────────────────
variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.40.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones to deploy subnets in."
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "enable_nat_instance" {
  description = "Deploy a NAT instance for private-subnet outbound traffic."
  type        = bool
  default     = true
}

variable "nat_instance_type" {
  description = "EC2 instance type for the NAT instance."
  type        = string
  default     = "t4g.nano"
}

# ─── RDS ──────────────────────────────────────────────────────────────────────
variable "rds_instance_class" {
  description = "RDS DB instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_multi_az" {
  description = "Enable Multi-AZ for the RDS instance."
  type        = bool
  default     = false
}

variable "rds_backup_retention_days" {
  description = "Days to retain automated RDS snapshots."
  type        = number
  default     = 35
}

variable "rds_deletion_protection" {
  description = "Prevent accidental deletion of the RDS instance."
  type        = bool
  default     = true
}

# ─── EKS ──────────────────────────────────────────────────────────────────────
variable "eks_cluster_version" {
  description = "Kubernetes version for the EKS control plane."
  type        = string
  default     = "1.35"
}

variable "redis_node_type" {
  description = "ElastiCache Redis node type."
  type        = string
  default     = "cache.t4g.micro"
}

# ─── S3 / Audit ───────────────────────────────────────────────────────────────
variable "audit_retention_days" {
  description = "Object Lock COMPLIANCE retention period in days for the audit bucket (2190 = 6 years, HIPAA/TEFCA minimum)."
  type        = number
  default     = 21
}

variable "alb_log_prefix" {
  description = "S3 key prefix for ALB access logs."
  type        = string
  default     = "alb"
}

# ─── Observability ────────────────────────────────────────────────────────────
variable "log_retention_days" {
  description = "Days to retain application logs in CloudWatch."
  type        = number
  default     = 14
}

# ─── ALB / WAF ────────────────────────────────────────────────────────────────
variable "enable_waf" {
  description = "Attach a WAFv2 Web ACL to the ALB."
  type        = bool
  default     = true
}

# ─── IAM / CI ─────────────────────────────────────────────────────────────────
variable "github_branch" {
  description = "Git branch allowed to assume the GitHub OIDC deploy role."
  type        = string
  default     = "main"
}

variable "tfstate_bucket" {
  description = "S3 bucket name holding Terraform remote state. Granted read/write to the GitHub deploy role so CI can run terraform plan/apply."
  type        = string
  default     = "chit-terraform-state-sydata"
}
