output "vpc_id" {
  description = "ID of the VPC (passed through from input; VPC is created in envs/prod)."
  value       = var.vpc_id
}

output "vpc_cidr" {
  description = "CIDR block of the VPC."
  value       = var.vpc_cidr
}

output "public_subnet_ids" {
  description = "IDs of both public subnets (us-east-1a, us-east-1b) — used by the ALB."
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "IDs of both app-private subnets (us-east-1a, us-east-1b) — used by the EKS control plane and general workloads."
  value       = aws_subnet.private[*].id
}

output "node_subnet_ids" {
  description = "Single-element list containing only the us-east-1a app-private subnet. Pass to the EKS managed node group so all pods and nodes land in us-east-1a."
  value       = [aws_subnet.private[0].id]
}

output "db_subnet_ids" {
  description = "IDs of both data subnets (us-east-1a, us-east-1b) — used by the RDS and ElastiCache subnet groups."
  value       = aws_subnet.db[*].id
}

output "db_subnet_group_name" {
  description = "Name of the aws_db_subnet_group. Pass to modules/rds."
  value       = aws_db_subnet_group.this.name
}

output "cache_subnet_group_name" {
  description = "Name of the aws_elasticache_subnet_group (uses the same data subnets as RDS). Pass to a future ElastiCache/Redis module."
  value       = aws_elasticache_subnet_group.this.name
}

output "nat_instance_id" {
  description = "Instance ID of the NAT instance (null when disabled)."
  value       = try(aws_instance.nat[0].id, null)
}
