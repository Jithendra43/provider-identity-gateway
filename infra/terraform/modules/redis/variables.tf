variable "name" {
	description = "Name/ID prefix for the ElastiCache Redis cluster."
	type        = string
}

variable "kms_key_arn" {
	description = "KMS key ARN used for Redis at-rest encryption."
	type        = string
}

variable "subnet_group_name" {
	description = "ElastiCache subnet group name (typically shared data subnets)."
	type        = string
}

variable "security_group_id" {
	description = "Security group ID attached to the Redis cluster."
	type        = string
}

variable "node_type" {
	description = "ElastiCache node type (for example cache.t4g.micro)."
	type        = string
	default     = "cache.t4g.micro"
}

variable "engine_version" {
	description = "Redis engine version."
	type        = string
	default     = "7.1"
}

variable "port" {
	description = "Redis port."
	type        = number
	default     = 6379
}

variable "primary_az" {
	description = "Preferred AZ for the single-node Redis cluster."
	type        = string
	default     = "us-east-1a"
}

variable "auth_token" {
	description = "Redis AUTH token (sensitive), sourced from modules/secrets."
	type        = string
	sensitive   = true
}
