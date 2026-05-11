# =============================================================================
# Alerting — single SNS topic for partner certificate expiry + future
# operational alerts. Encrypted with the shared CMK so all topic traffic is
# audit-compliant. Email subscription is optional; when provided, AWS sends
# a confirmation link to the address before publishing begins.
# =============================================================================

resource "aws_sns_topic" "alerts" {
  name              = "${var.name_prefix}-alerts"
  kms_master_key_id = var.kms_key_arn

  tags = {
    Purpose = "tefca-partner-and-ops-alerts"
  }
}

# Allow CloudWatch Alarms (in this account) and the ECS task role's principal
# to publish. The task role itself is granted sns:Publish in modules/iam.
resource "aws_sns_topic_policy" "alerts" {
  arn    = aws_sns_topic.alerts.arn
  policy = data.aws_iam_policy_document.alerts.json
}

data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "alerts" {
  statement {
    sid     = "AllowAccountPublish"
    actions = ["SNS:Publish"]
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
    resources = [aws_sns_topic.alerts.arn]
  }

  statement {
    sid     = "AllowCloudWatchPublish"
    actions = ["SNS:Publish"]
    principals {
      type        = "Service"
      identifiers = ["cloudwatch.amazonaws.com"]
    }
    resources = [aws_sns_topic.alerts.arn]
  }
}

resource "aws_sns_topic_subscription" "email" {
  count                  = var.alert_email == "" ? 0 : 1
  topic_arn              = aws_sns_topic.alerts.arn
  protocol               = "email"
  endpoint               = var.alert_email
  endpoint_auto_confirms = false
}
