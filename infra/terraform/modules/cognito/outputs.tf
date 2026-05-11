output "user_pool_arn" {
  description = "ARN of the Cognito user pool."
  value       = aws_cognito_user_pool.admin.arn
}

output "user_pool_id" {
  description = "ID of the Cognito user pool."
  value       = aws_cognito_user_pool.admin.id
}

output "client_id" {
  description = "Cognito app client ID."
  value       = aws_cognito_user_pool_client.admin.id
}

output "client_secret" {
  description = "Cognito app client secret (sensitive)."
  value       = aws_cognito_user_pool_client.admin.client_secret
  sensitive   = true
}

output "domain" {
  description = "Cognito hosted UI domain (used to build the authorization endpoint URL)."
  value       = aws_cognito_user_pool_domain.admin.domain
}

output "issuer_uri" {
  description = "OIDC issuer URI for the Cognito user pool."
  value       = "https://cognito-idp.${data.aws_region.here.name}.amazonaws.com/${aws_cognito_user_pool.admin.id}"
}
