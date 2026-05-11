output "alb_arn" {
  description = "ARN of the Application Load Balancer."
  value       = aws_lb.this.arn
}

output "alb_arn_suffix" {
  description = "ARN suffix of the ALB (used in CloudWatch metrics)."
  value       = aws_lb.this.arn_suffix
}

output "alb_dns_name" {
  description = "DNS name of the ALB; point your domain CNAME here."
  value       = aws_lb.this.dns_name
}

output "alb_sg_id" {
  description = "Security group ID attached to the ALB."
  value       = var.alb_sg_id
}

output "gateway_target_group_arn" {
  description = "ARN of the mTLS partner gateway target group."
  value       = aws_lb_target_group.gateway.arn
}

output "gateway_target_group_arn_suffix" {
  description = "ARN suffix of the gateway target group."
  value       = aws_lb_target_group.gateway.arn_suffix
}

output "admin_target_group_arn" {
  description = "ARN of the admin UI target group."
  value       = aws_lb_target_group.admin.arn
}
