output "github_deploy_role_arn" {
  description = "ARN of the GitHub OIDC deploy role; set as AWS_DEPLOY_ROLE_ARN in GitHub Actions secrets."
  value       = aws_iam_role.github_deploy.arn
}

output "ecr_repository_url" {
  description = "ECR repository URL used as the image push target in CI."
  value       = aws_ecr_repository.app.repository_url
}
