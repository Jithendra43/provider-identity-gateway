variable "vpc_id" {
  description = "ID of the existing VPC created in envs/prod/main.tf. Passed in so that modules/securitygroups can also receive vpc_id without creating a Terraform module cycle."
  type        = string
}

variable "name" {
  description = "Name prefix for VPC and all networking resources."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC (e.g. 10.40.0.0/16)."
  type        = string
}

variable "availability_zones" {
  description = "List of availability zones to create public and private subnets in."
  type        = list(string)
}

variable "enable_nat_instance" {
  description = "When true, deploy a t4g.nano NAT instance for private-subnet outbound traffic."
  type        = bool
  default     = true
}

variable "nat_instance_type" {
  description = "EC2 instance type for the NAT instance."
  type        = string
  default     = "t4g.nano"
}

variable "nat_sg_id" {
  description = "Security group ID for the NAT instance, provided by the securitygroups module. Keeping this as an input breaks the internal SG creation and moves all SGs to the securitygroups module per infra-guidelines."
  type        = string
}

variable "eks_cluster_name" {
  description = "EKS cluster name for Kubernetes subnet tags (kubernetes.io/cluster/<name> and elb role tags). Leave empty to skip."
  type        = string
  default     = ""
}
