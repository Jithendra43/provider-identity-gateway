output "cmk_arn" {
  description = "ARN of the customer-managed KMS key."
  value       = aws_kms_key.this.arn
}

output "cmk_id" {
  description = "Key ID of the customer-managed KMS key."
  value       = aws_kms_key.this.id
}
