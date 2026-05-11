variable "name" {
  description = "Name prefix for RDS instance, subnet group, and parameter group."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID used by the RDS security group (provided by securitygroups module)."
  type        = string
}

variable "db_subnet_group_name" {
  description = "Name of the aws_db_subnet_group to attach the RDS instance to. Created by modules/network and passed in here."
  type        = string
}

variable "primary_az" {
  description = "Availability zone to place the RDS instance in when multi_az is false. Should match the AZ where EKS pods run to minimise cross-AZ data transfer costs."
  type        = string
  default     = "us-east-1a"
}

variable "allowed_sg_ids" {
  description = "Source security group IDs for inline Postgres ingress rules. Pass [] to skip inline rules and add them separately (prevents EKS cycle)."
  type        = list(string)
  default     = []
}

variable "kms_key_arn" {
  description = "KMS key ARN for RDS storage encryption and Performance Insights."
  type        = string
}

variable "instance_class" {
  description = "RDS DB instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "multi_az" {
  description = "Enable Multi-AZ standby for high availability."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  description = "Number of days to retain automated RDS snapshots."
  type        = number
  default     = 35
}

variable "deletion_protection" {
  description = "Prevent accidental deletion of the RDS instance."
  type        = bool
  default     = true
}

variable "rds_sg_id" {
  description = "Security group ID for the RDS instance, supplied by the securitygroups module."
  type        = string
}
