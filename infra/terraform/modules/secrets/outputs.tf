output "task_secret_refs" {
  description = "Map of secret ARN path references for Kubernetes External Secrets / legacy ECS valueFrom (kept for backwards compatibility)."
  value = {
    DB_URL                = "${aws_secretsmanager_secret.db.arn}:url::"
    DB_USERNAME           = "${aws_secretsmanager_secret.db.arn}:username::"
    DB_PASSWORD           = "${aws_secretsmanager_secret.db.arn}:password::"
    REDIS_AUTH_TOKEN      = "${aws_secretsmanager_secret.redis.arn}:auth_token::"
    HMAC_SECRET           = aws_secretsmanager_secret.hmac.arn
    COGNITO_CLIENT_SECRET = aws_ssm_parameter.cognito_client_secret.arn
  }
}

output "all_arns" {
  description = "All Secrets Manager + SSM ARNs — used to scope IRSA policies for External Secrets and the gateway pod."
  value = [
    aws_secretsmanager_secret.db.arn,
    aws_secretsmanager_secret.redis.arn,
    aws_secretsmanager_secret.hmac.arn,
    aws_ssm_parameter.jwt_issuer.arn,
    aws_ssm_parameter.jwt_jwk_set_uri.arn,
    aws_ssm_parameter.jwt_audience.arn,
    aws_ssm_parameter.cognito_client_secret.arn,
    aws_ssm_parameter.redis_host.arn,
  ]
}

output "db_secret_arn" {
  description = "ARN of the RDS credentials Secrets Manager secret."
  value       = aws_secretsmanager_secret.db.arn
}

output "redis_secret_arn" {
  description = "ARN of the Redis auth-token Secrets Manager secret."
  value       = aws_secretsmanager_secret.redis.arn
}

output "redis_auth_token" {
  description = "Redis AUTH token used by the ElastiCache cluster."
  value       = random_password.redis_auth.result
  sensitive   = true
}
