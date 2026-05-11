resource "random_password" "db" {
  length           = 32
  special          = true
  override_special = "!#$%*-_=+[]{}"
}

resource "aws_db_parameter_group" "pg" {
  family = "postgres15"
  name   = "${var.name}-pg15"
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
  parameter {
    name  = "log_connections"
    value = "1"
  }
  parameter {
    name  = "log_disconnections"
    value = "1"
  }
}

resource "aws_db_instance" "this" {
  identifier                          = var.name
  engine                              = "postgres"
  engine_version                      = "15.14"
  instance_class                      = var.instance_class
  allocated_storage                   = 20
  max_allocated_storage               = 100
  storage_type                        = "gp3"
  storage_encrypted                   = true
  kms_key_id                          = var.kms_key_arn
  username                            = "tefca_admin"
  password                            = random_password.db.result
  db_name                             = "tefca"
  port                                = 5432
  multi_az                            = var.multi_az
  availability_zone                   = var.multi_az ? null : var.primary_az
  publicly_accessible                 = false
  vpc_security_group_ids              = [var.rds_sg_id]
  db_subnet_group_name                = var.db_subnet_group_name
  parameter_group_name                = aws_db_parameter_group.pg.name
  backup_retention_period             = var.backup_retention_days
  backup_window                       = "07:00-08:00"
  maintenance_window                  = "Sun:08:00-Sun:09:00"
  copy_tags_to_snapshot               = true
  deletion_protection                 = var.deletion_protection
  skip_final_snapshot                 = false
  final_snapshot_identifier           = "${var.name}-final-${formatdate("YYYYMMDDhhmmss", timestamp())}"
  performance_insights_enabled        = true
  performance_insights_kms_key_id     = var.kms_key_arn
  enabled_cloudwatch_logs_exports     = ["postgresql", "upgrade"]
  iam_database_authentication_enabled = true
  auto_minor_version_upgrade          = true
  apply_immediately                   = false

  lifecycle {
    ignore_changes = [final_snapshot_identifier, password]
  }
}


