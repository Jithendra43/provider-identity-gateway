resource "aws_elasticache_replication_group" "this" {
	replication_group_id       = var.name
	description                = "${var.name} redis"
	engine                     = "redis"
	engine_version             = var.engine_version
	node_type                  = var.node_type
	num_cache_clusters         = 1
	port                       = var.port
	subnet_group_name          = var.subnet_group_name
	security_group_ids         = [var.security_group_id]
	preferred_cache_cluster_azs = [var.primary_az]

	at_rest_encryption_enabled = true
	transit_encryption_enabled = true
	auth_token                 = var.auth_token
	kms_key_id                 = var.kms_key_arn

	automatic_failover_enabled = false
	auto_minor_version_upgrade = true
	apply_immediately          = false
}
