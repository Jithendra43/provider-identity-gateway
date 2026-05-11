# =============================================================================
# Network — subnets, routing, IGW, and NAT instance on top of an existing VPC.
# The VPC itself (aws_vpc, VPC flow logs, flow-log IAM) is provisioned in
# envs/prod/main.tf so that modules/securitygroups can receive vpc_id without
# creating a Terraform module-reference cycle:
#   envs/prod creates VPC → passes vpc_id to BOTH securitygroups & network
#   network receives nat_sg_id from securitygroups — no mutual dependency.
# =============================================================================

# -----------------------------------------------------------------------------
# Subnets — public (ALB, NAT instance) + private (EKS nodes, RDS)
# -----------------------------------------------------------------------------
resource "aws_subnet" "public" {
  count                   = length(var.availability_zones)
  vpc_id                  = var.vpc_id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = false
  tags = merge(
    { Name = "${var.name}-public-${count.index}", Tier = "public" },
    var.eks_cluster_name == "" ? {} : {
      "kubernetes.io/cluster/${var.eks_cluster_name}" = "shared"
      "kubernetes.io/role/elb"                          = "1"
    }
  )
}

resource "aws_subnet" "private" {
  count             = length(var.availability_zones)
  vpc_id            = var.vpc_id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone = var.availability_zones[count.index]
  tags = merge(
    { Name = "${var.name}-private-${count.index}", Tier = "private" },
    var.eks_cluster_name == "" ? {} : {
      "kubernetes.io/cluster/${var.eks_cluster_name}" = "shared"
      "kubernetes.io/role/internal-elb"              = "1"
    }
  )
}

resource "aws_internet_gateway" "this" {
  vpc_id = var.vpc_id
  tags   = { Name = var.name }
}

resource "aws_route_table" "public" {
  vpc_id = var.vpc_id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }
  tags = { Name = "${var.name}-public" }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# -----------------------------------------------------------------------------
# NAT instance — t4g.nano ARM (fck-nat), cost ~$3/mo vs ~$32/mo managed NAT GW
# Single instance for outbound traffic from private subnets (JWKS fetch, etc.)
# SG is managed by modules/securitygroups and passed in as var.nat_sg_id.
# -----------------------------------------------------------------------------
data "aws_ami" "nat" {
  count       = var.enable_nat_instance ? 1 : 0
  most_recent = true
  owners      = ["568608671756"] # fck-nat publisher
  filter {
    name   = "name"
    values = ["fck-nat-al2023-hvm-*-arm64-ebs"]
  }
}

resource "aws_instance" "nat" {
  count                       = var.enable_nat_instance ? 1 : 0
  ami                         = data.aws_ami.nat[0].id
  instance_type               = var.nat_instance_type
  subnet_id                   = aws_subnet.public[0].id
  source_dest_check           = false
  vpc_security_group_ids      = [var.nat_sg_id]
  associate_public_ip_address = true
  metadata_options {
    http_tokens   = "required" # IMDSv2 only
    http_endpoint = "enabled"
  }
  root_block_device {
    encrypted   = true
    volume_size = 8
  }
  tags = { Name = "${var.name}-nat" }
}

resource "aws_route_table" "private" {
  vpc_id = var.vpc_id
  dynamic "route" {
    for_each = var.enable_nat_instance ? [1] : []
    content {
      cidr_block           = "0.0.0.0/0"
      network_interface_id = aws_instance.nat[0].primary_network_interface_id
    }
  }
  tags = { Name = "${var.name}-private" }
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# -----------------------------------------------------------------------------
# Data subnets — shared by RDS (PostgreSQL) and ElastiCache (Redis).
# Span both AZs so AWS subnet group requirements are satisfied.
# RDS instance and Redis cluster are both pinned to us-east-1a at the
# resource level (availability_zone / preferred_az), not here.
# CIDRs use offsets 20, 21 to keep them separate from app-private (10, 11).
# -----------------------------------------------------------------------------
resource "aws_subnet" "db" {
  count             = length(var.availability_zones)
  vpc_id            = var.vpc_id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 20)
  availability_zone = var.availability_zones[count.index]
  tags = {
    Name = "${var.name}-data-${count.index}"
    Tier = "data"
  }
}

resource "aws_route_table_association" "db" {
  count          = length(aws_subnet.db)
  subnet_id      = aws_subnet.db[count.index].id
  route_table_id = aws_route_table.private.id
}

# RDS subnet group — spans both AZs (AWS minimum requirement)
resource "aws_db_subnet_group" "this" {
  name       = "${var.name}-data"
  subnet_ids = aws_subnet.db[*].id
  tags       = { Name = "${var.name}-data" }
}

# ElastiCache subnet group — reuses the same data subnets as RDS.
# Redis node itself is pinned to us-east-1a via preferred_availability_zone.
resource "aws_elasticache_subnet_group" "this" {
  name       = "${var.name}-data"
  subnet_ids = aws_subnet.db[*].id
  tags       = { Name = "${var.name}-data" }
}

