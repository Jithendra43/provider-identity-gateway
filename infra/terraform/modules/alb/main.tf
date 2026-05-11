resource "aws_lb" "this" {
  name                       = var.name
  load_balancer_type         = "application"
  internal                   = false
  security_groups            = [var.alb_sg_id]
  subnets                    = var.public_subnet_ids
  drop_invalid_header_fields = true
  enable_deletion_protection = true
  enable_http2               = true

  access_logs {
    bucket  = var.access_logs_bucket
    enabled = true
    prefix  = "alb"
  }
}

# -----------------------------------------------------------------------------
# Target groups — gateway (mTLS partner traffic) + admin (Cognito-protected)
# Both point at the SAME Kubernetes pods (port 8080), distinguished by listener.
# -----------------------------------------------------------------------------
resource "aws_lb_target_group" "gateway" {
  name                 = "${var.name}-gw"
  port                 = 8080
  protocol             = "HTTP"
  vpc_id               = var.vpc_id
  target_type          = "ip"
  deregistration_delay = 30
  health_check {
    path                = "/actuator/health/readiness"
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_target_group" "admin" {
  name        = "${var.name}-admin"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"
  health_check {
    path    = "/actuator/health/readiness"
    matcher = "200"
  }
}

# -----------------------------------------------------------------------------
# :443 listener — partner mTLS (mode=verify)
# -----------------------------------------------------------------------------
resource "aws_lb_listener" "mtls" {
  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  # FIPS 140-3 validated cipher suite policy. AWS-managed, restricted to
  # NIST-approved algorithms; required for FedRAMP / TEFCA FIPS posture.
  ssl_policy      = "ELBSecurityPolicy-TLS13-1-2-FIPS-2023-04"
  certificate_arn = var.acm_server_cert_arn

  mutual_authentication {
    mode                             = "verify"
    trust_store_arn                  = var.trust_store_arn
    ignore_client_certificate_expiry = false
  }

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }
}

# -----------------------------------------------------------------------------
# :8444 listener — admin UI. Auth is enforced inside the Spring Boot app
# (/api/admin/auth/login + httpOnly session cookie). The ALB is just a TLS +
# routing perimeter for this listener.
# -----------------------------------------------------------------------------
resource "aws_lb_listener" "admin" {
  load_balancer_arn = aws_lb.this.arn
  port              = 8444
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-FIPS-2023-04"
  certificate_arn   = var.acm_server_cert_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.admin.arn
  }
}

# -----------------------------------------------------------------------------
# WAFv2 — lite ruleset (managed AWS rules) attached to the ALB
# -----------------------------------------------------------------------------
resource "aws_wafv2_web_acl" "this" {
  count = var.enable_waf ? 1 : 0
  name  = "${var.name}-waf"
  scope = "REGIONAL"
  default_action {
    allow {}
  }
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.name}-waf"
    sampled_requests_enabled   = true
  }
  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 1
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        vendor_name = "AWS"
        name        = "AWSManagedRulesCommonRuleSet"

        # Admin proxy mutation requests can trigger false positives on
        # URL-like values in JSON bodies/query args. Keep visibility by
        # counting these rules instead of blocking while retaining all
        # other protections in block mode.
        rule_action_override {
          name = "GenericRFI_BODY"
          action_to_use {
            count {}
          }
        }

        rule_action_override {
          name = "EC2MetaDataSSRF_BODY"
          action_to_use {
            count {}
          }
        }

        rule_action_override {
          name = "GenericRFI_QUERYARGUMENTS"
          action_to_use {
            count {}
          }
        }

        # Enable only if logs prove legitimate requests are blocked by
        # body size inspection.
        # rule_action_override {
        #   name = "SizeRestrictions_BODY"
        #   action_to_use {
        #     count {}
        #   }
        # }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "common"
      sampled_requests_enabled   = true
    }
  }
  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 2
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        vendor_name = "AWS"
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "bad-inputs"
      sampled_requests_enabled   = true
    }
  }
  rule {
    name     = "RateLimit"
    priority = 3
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "rate-limit"
      sampled_requests_enabled   = true
    }
  }
}

resource "aws_wafv2_web_acl_association" "alb" {
  count        = var.enable_waf ? 1 : 0
  resource_arn = aws_lb.this.arn
  web_acl_arn  = aws_wafv2_web_acl.this[0].arn
}


