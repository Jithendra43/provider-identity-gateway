# =============================================================================
# Security Groups — consolidated per infra-guidelines
# All application-tier SGs live here: ALB, RDS, NAT instance.
#
# ⚠️  DEPENDENCY NOTE:
#   This module receives vpc_id from module.network (so it depends on network).
#   module.network receives nat_sg_id from this module (so network depends on
#   securitygroups). Terraform cannot resolve this mutual dependency.
#
#   Resolution path (to be done when the network module is split):
#     1. Extract aws_vpc creation into a new modules/vpc module.
#     2. modules/securitygroups takes vpc_id from modules/vpc.
#     3. modules/network takes nat_sg_id from modules/securitygroups.
#     4. Dependency graph becomes: vpc → securitygroups → network (linear).
# =============================================================================

# -----------------------------------------------------------------------------
# NAT Instance Security Group
# Accepts all inbound from within the VPC CIDR (private subnet outbound
# traffic is masqueraded through the NAT instance). Egress unrestricted.
# -----------------------------------------------------------------------------
resource "aws_security_group" "nat" {
  name        = "${var.name_prefix}-nat"
  description = "NAT instance: accept all inbound from VPC CIDR, unrestricted egress"
  vpc_id      = var.vpc_id

  ingress {
    description = "All traffic from within the VPC (private subnet outbound)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name_prefix}-nat" }

  lifecycle {
    create_before_destroy = true
  }
}

# -----------------------------------------------------------------------------
# ALB Security Group
# :443  — partner mTLS traffic (mutual TLS, cert required)
# :8444 — admin UI traffic (Cognito OAuth2 protected)
# -----------------------------------------------------------------------------
resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-alb"
  description = "Internet-facing ALB: :443 mTLS partner traffic and :8444 admin UI"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTPS mTLS partner traffic"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS admin UI traffic"
    from_port   = 8444
    to_port     = 8444
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name_prefix}-alb" }

  lifecycle {
    create_before_destroy = true
  }
}

# -----------------------------------------------------------------------------
# RDS Security Group
# Inline ingress rules are intentionally left empty; the EKS-node ingress rule
# is added in envs/prod/main.tf via aws_vpc_security_group_ingress_rule to
# avoid a Terraform cycle between the rds and eks modules.
# -----------------------------------------------------------------------------
resource "aws_security_group" "rds" {
  name        = "${var.name_prefix}-rds"
  description = "RDS PostgreSQL: ingress from EKS nodes added post-apply via root module SG rule"
  vpc_id      = var.vpc_id

  dynamic "ingress" {
    for_each = length(var.allowed_rds_sg_ids) > 0 ? [1] : []
    content {
      description     = "PostgreSQL from allowed security groups"
      from_port       = 5432
      to_port         = 5432
      protocol        = "tcp"
      security_groups = var.allowed_rds_sg_ids
    }
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name_prefix}-rds" }

  lifecycle {
    create_before_destroy = true
  }
}

# -----------------------------------------------------------------------------
# Redis (ElastiCache) Security Group
# Inline ingress rules intentionally empty; EKS-node ingress rule is added in
# envs/prod/main.tf via aws_vpc_security_group_ingress_rule (same pattern as
# RDS) to avoid a module cycle between securitygroups and eks.
# -----------------------------------------------------------------------------
resource "aws_security_group" "redis" {
  name        = "${var.name_prefix}-redis"
  description = "ElastiCache Redis: ingress from EKS nodes added post-apply via root module SG rule"
  vpc_id      = var.vpc_id

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.name_prefix}-redis" }

  lifecycle {
    create_before_destroy = true
  }
}
