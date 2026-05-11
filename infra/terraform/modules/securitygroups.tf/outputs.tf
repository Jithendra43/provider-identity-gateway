output "nat_sg_id" {
  description = "Security group ID for the NAT instance."
  value       = aws_security_group.nat.id
}

output "alb_sg_id" {
  description = "Security group ID for the internet-facing Application Load Balancer."
  value       = aws_security_group.alb.id
}

output "rds_sg_id" {
  description = "Security group ID for the RDS PostgreSQL instance."
  value       = aws_security_group.rds.id
}

output "redis_sg_id" {
  description = "Security group ID for the ElastiCache Redis cluster."
  value       = aws_security_group.redis.id
}
