variable "name" {
  description = "Name prefix for ACM and trust-store resources."
  type        = string
}

variable "domain_name" {
  description = "Domain name for the ACM certificate (e.g. gateway.example.gov)."
  type        = string
}

variable "trust_store_bucket" {
  description = "S3 bucket name where partner CA PEM objects and the bundle are uploaded."
  type        = string
}

variable "partner_cas" {
  description = "Map of partner alias => PEM-encoded root CA for the ALB mTLS trust store."
  type        = map(string)
  default     = {}
}

variable "use_self_signed_server_cert" {
  description = "If true, generate a self-signed cert and import to ACM (bootstrap mode, no DNS validation)."
  type        = bool
  default     = false
}
