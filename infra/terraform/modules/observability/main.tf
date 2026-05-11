resource "aws_cloudwatch_log_group" "app" {
  name              = "/kubernetes/${var.name_prefix}/gateway"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
}

# Composite alarm wrapper would go here; minimal viable alarms below
resource "aws_cloudwatch_metric_alarm" "task_5xx" {
  alarm_name          = "${var.name_prefix}-alb-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_ELB_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 10
  treat_missing_data  = "notBreaching"
  alarm_description   = "ALB returning >10 5XX/min"
}


