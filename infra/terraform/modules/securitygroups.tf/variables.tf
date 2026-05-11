variable "name_prefix" {
  description = "Name prefix for all security group names (e.g. tefca-gw-prod)."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID in which to create the security groups."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block of the VPC; used for RDS and NAT ingress rules where needed."
  type        = string
}

variable "allowed_rds_sg_ids" {
  description = "Source security group IDs allowed to reach RDS on :5432. Pass [] here and add the EKS node SG rule separately via aws_vpc_security_group_ingress_rule to avoid module cycles."
  type        = list(string)
  default     = []
}
