output "audit_bucket" {
  description = "Name of the WORM-protected audit S3 bucket."
  value       = aws_s3_bucket.this["audit"].bucket
}

output "audit_bucket_arn" {
  description = "ARN of the audit S3 bucket."
  value       = aws_s3_bucket.this["audit"].arn
}

output "backup_bucket" {
  description = "Name of the encrypted backup S3 bucket."
  value       = aws_s3_bucket.this["backup"].bucket
}

output "backup_bucket_arn" {
  description = "ARN of the backup S3 bucket."
  value       = aws_s3_bucket.this["backup"].arn
}

output "alb_logs_bucket" {
  description = "Name of the ALB access-logs S3 bucket."
  value       = aws_s3_bucket.this["alb_logs"].bucket
}

output "trust_store_bucket" {
  description = "Name of the ALB mTLS trust-store S3 bucket."
  value       = aws_s3_bucket.this["trust_store"].bucket
}
