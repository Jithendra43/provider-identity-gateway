output "id" {
	description = "ElastiCache Redis cluster ID."
	value       = aws_elasticache_replication_group.this.id
}

output "arn" {
	description = "ElastiCache Redis cluster ARN."
	value       = aws_elasticache_replication_group.this.arn
}

output "endpoint" {
	description = "Redis endpoint in host:port format."
	value       = "${aws_elasticache_replication_group.this.primary_endpoint_address}:${aws_elasticache_replication_group.this.port}"
}

output "address" {
	description = "Redis node address."
	value       = aws_elasticache_replication_group.this.primary_endpoint_address
}

# Alias used by the secrets module to populate /${name_prefix}/redis/host in SSM.
output "host" {
	description = "Redis primary endpoint hostname (no port). Alias for address."
	value       = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "port" {
	description = "Redis port."
	value       = aws_elasticache_replication_group.this.port
}
