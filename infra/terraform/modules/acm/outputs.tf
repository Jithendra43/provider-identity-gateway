output "server_cert_arn" {
  description = "ARN of the ACM certificate attached to the ALB HTTPS listeners."
  value       = local.server_cert_arn
}

output "trust_store_arn" {
  description = "ARN of the ALB mTLS trust store backed by partner CA PEMs in S3."
  value       = aws_lb_trust_store.this.arn
}
