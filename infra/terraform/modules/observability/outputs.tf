output "log_group_name" {
  description = "Name of the CloudWatch log group for application logs."
  value       = aws_cloudwatch_log_group.app.name
}

output "log_group_arn" {
  description = "ARN of the CloudWatch log group."
  value       = aws_cloudwatch_log_group.app.arn
}
