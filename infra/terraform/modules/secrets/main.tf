# DB credential — Secrets Manager (rotation-ready)
resource "aws_secretsmanager_secret" "db" {
  name                    = "${var.name_prefix}/db"
  kms_key_id              = var.kms_key_arn
  description             = "RDS PostgreSQL credentials (rotated)"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = var.rds_username
    password = var.rds_password
    engine   = "postgres"
    host     = split(":", var.rds_endpoint)[0]
    port     = 5432
    dbname   = var.rds_db_name
    url      = "jdbc:postgresql://${var.rds_endpoint}/${var.rds_db_name}?sslmode=require"
  })
}

resource "random_password" "redis_auth" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "redis" {
  name                    = "${var.name_prefix}/redis"
  kms_key_id              = var.kms_key_arn
  description             = "Redis auth token"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "redis" {
  secret_id = aws_secretsmanager_secret.redis.id
  secret_string = jsonencode({
    auth_token = random_password.redis_auth.result
  })
}

# HMAC secret — auto-generated, stored in Secrets Manager (KMS-encrypted)
resource "random_password" "hmac" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "hmac" {
  name                    = "${var.name_prefix}/hmac/secret"
  kms_key_id              = var.kms_key_arn
  description             = "HMAC signing secret for ingress-auth-service (Terraform-managed, rotate via Secrets Manager)"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "hmac" {
  secret_id     = aws_secretsmanager_secret.hmac.id
  secret_string = random_password.hmac.result
}

# Other secrets — SSM SecureString (free, KMS-encrypted, no auto-rotation)

resource "aws_ssm_parameter" "jwt_issuer" {
  name  = "/${var.name_prefix}/jwt/issuer-uri"
  type  = "String"
  value = var.jwt_issuer_uri
}

resource "aws_ssm_parameter" "jwt_jwk_set_uri" {
  name  = "/${var.name_prefix}/jwt/jwk-set-uri"
  type  = "String"
  value = var.jwt_jwk_set_uri
}

resource "aws_ssm_parameter" "jwt_audience" {
  name  = "/${var.name_prefix}/jwt/audience"
  type  = "String"
  value = var.jwt_audience
}

# Cognito app-client secret — Spring Security OIDC client uses this to
# authenticate the back-channel /oauth2/token call when exchanging the
# authorization code returned by the Hosted UI.
resource "aws_ssm_parameter" "cognito_client_secret" {
  name   = "/${var.name_prefix}/cognito/client-secret"
  type   = "SecureString"
  value  = var.cognito_client_secret
  key_id = var.kms_key_arn
  tier   = "Standard"
}


