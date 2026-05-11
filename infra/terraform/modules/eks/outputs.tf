output "cluster_name" {
  value = module.eks.cluster_name
}

output "cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "cluster_certificate_authority_data" {
  value = module.eks.cluster_certificate_authority_data
}

output "cluster_arn" {
  value = module.eks.cluster_arn
}

output "oidc_provider_arn" {
  value = module.eks.oidc_provider_arn
}

output "fargate_pod_security_group_id" {
  value = module.eks.cluster_primary_security_group_id
}

output "node_security_group_id" {
  value = module.eks.cluster_primary_security_group_id
}

output "irsa_lbc_role_arn" {
  value = module.irsa_lbc.iam_role_arn
}

output "irsa_external_secrets_role_arn" {
  value = module.irsa_external_secrets.iam_role_arn
}

output "irsa_gateway_pod_role_arn" {
  value = module.irsa_gateway_pod.iam_role_arn
}
