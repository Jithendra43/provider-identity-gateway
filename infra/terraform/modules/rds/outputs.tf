output "endpoint" {
  description = "RDS instance endpoint in host:port format."
  value       = aws_db_instance.this.endpoint
}

output "username" {
  description = "RDS master username."
  value       = aws_db_instance.this.username
}

output "password" {
  description = "RDS master password (sensitive — read from Secrets Manager in production)."
  value       = random_password.db.result
  sensitive   = true
}

output "db_name" {
  description = "Database name."
  value       = aws_db_instance.this.db_name
}

output "arn" {
  description = "ARN of the RDS DB instance."
  value       = aws_db_instance.this.arn
}

output "security_group_id" {
  description = "Security group ID attached to the RDS instance."
  value       = var.rds_sg_id
}
