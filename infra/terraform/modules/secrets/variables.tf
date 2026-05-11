variable "name_prefix" {
  description = "Prefix for Secrets Manager and SSM parameter names (e.g. tefca-gw-prod)."
  type        = string
}

variable "kms_key_arn" {
  description = "KMS key ARN for encrypting Secrets Manager secrets and SSM SecureString parameters."
  type        = string
}

variable "rds_username" {
  description = "RDS master username stored in the DB JSON secret."
  type        = string
}

variable "rds_password" {
  description = "RDS master password stored in the DB JSON secret."
  type        = string
  sensitive   = true
}

variable "rds_endpoint" {
  description = "RDS endpoint (host:port) stored in the DB JSON secret."
  type        = string
}

variable "rds_db_name" {
  description = "Database name stored in the DB JSON secret."
  type        = string
}

variable "jwt_issuer_uri" {
  description = "JWT issuer URI stored as an SSM plain-text parameter."
  type        = string
}

variable "jwt_jwk_set_uri" {
  description = "JWKS endpoint URL for the partner IdP. Stored in SSM and passed to the ingress-auth-service as OAUTH2_JWK_SET_URI."
  type        = string
}

variable "jwt_audience" {
  description = "JWT audience value stored as an SSM plain-text parameter."
  type        = string
}

variable "cognito_client_secret" {
  description = "Cognito app-client secret stored as an SSM SecureString (used by Spring Security oauth2Login)."
  type        = string
  sensitive   = true
}

variable "redis_endpoint" {
  description = "Redis primary endpoint address (host only, no port). Stored in SSM so the gateway pod can resolve it at startup."
  type        = string
}
