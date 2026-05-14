output "gateway_url" { value = "https://${var.gateway_domain}" }
output "admin_url" { value = "https://${var.admin_domain}" }
output "alb_dns_name" { value = module.alb.alb_dns_name }
output "ecr_repository_url" { value = module.iam.ecr_repository_url }
output "audit_bucket" { value = module.s3.audit_bucket }
output "rds_endpoint" { value = module.rds.endpoint }
output "redis_endpoint" { value = module.redis.endpoint }
output "kms_cmk_arn" { value = module.kms.cmk_arn }

# EKS / CD
output "eks_cluster_name" { value = module.eks.cluster_name }
output "eks_cluster_endpoint" { value = module.eks.cluster_endpoint }
output "eks_oidc_provider_arn" { value = module.eks.oidc_provider_arn }
output "eks_irsa_gateway_pod_role_arn" { value = module.eks.irsa_gateway_pod_role_arn }
output "eks_irsa_lbc_role_arn" { value = module.eks.irsa_lbc_role_arn }
output "eks_irsa_external_secrets_role_arn" { value = module.eks.irsa_external_secrets_role_arn }
output "eks_fargate_pod_security_group_id" { value = module.eks.fargate_pod_security_group_id }
output "eks_node_security_group_id" { value = module.eks.fargate_pod_security_group_id }
output "vpc_id" { value = aws_vpc.main.id }
output "vpc_cidr" { value = aws_vpc.main.cidr_block }
output "public_subnet_ids" { value = module.network.public_subnet_ids }
output "private_subnet_ids" { value = module.network.private_subnet_ids }
output "db_subnet_group_name" { value = module.network.db_subnet_group_name }
output "cache_subnet_group_name" { value = module.network.cache_subnet_group_name }
