output "topic_arn" {
  description = "ARN of the alerts SNS topic. Wire into the ECS task as TEFCA_ALERTS_SNS_TOPIC_ARN."
  value       = aws_sns_topic.alerts.arn
}

output "topic_name" {
  value = aws_sns_topic.alerts.name
}
