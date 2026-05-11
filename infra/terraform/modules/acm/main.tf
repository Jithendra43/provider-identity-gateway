# -----------------------------------------------------------------------------
# Public ALB cert via ACM DNS validation against the user-managed Route53 zone.
# -----------------------------------------------------------------------------
resource "aws_acm_certificate" "alb" {
  count             = var.use_self_signed_server_cert ? 0 : 1
  domain_name       = var.domain_name
  validation_method = "DNS"
  lifecycle {
    create_before_destroy = true
  }
}

# Bootstrap-only: self-signed cert imported to ACM so listeners can attach
# before a real domain is owned. Replace by setting use_self_signed_server_cert
# = false and adding DNS validation records.
resource "tls_private_key" "server" {
  count     = var.use_self_signed_server_cert ? 1 : 0
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_self_signed_cert" "server" {
  count           = var.use_self_signed_server_cert ? 1 : 0
  private_key_pem = tls_private_key.server[0].private_key_pem
  subject {
    common_name  = var.domain_name
    organization = "TEFCA Gateway bootstrap"
  }
  dns_names             = [var.domain_name]
  validity_period_hours = 24 * 365 * 2  # 2 years to avoid re-generation
  allowed_uses          = ["digital_signature", "key_encipherment", "server_auth"]
}

# Delay ACM import to avoid "valid in the future" clock skew errors
resource "time_sleep" "acm_import_delay" {
  count            = var.use_self_signed_server_cert ? 1 : 0
  create_duration  = "5s"
  depends_on       = [tls_self_signed_cert.server]
}

resource "aws_acm_certificate" "alb_self_signed" {
  count            = var.use_self_signed_server_cert ? 1 : 0
  private_key      = tls_private_key.server[0].private_key_pem
  certificate_body = tls_self_signed_cert.server[0].cert_pem
  depends_on       = [time_sleep.acm_import_delay]
  lifecycle {
    create_before_destroy = true
  }
}

locals {
  server_cert_arn = var.use_self_signed_server_cert ? aws_acm_certificate.alb_self_signed[0].arn : aws_acm_certificate.alb[0].arn
  bundle_pem      = length(var.partner_cas) > 0 ? join("\n", [for _, pem in var.partner_cas : pem]) : tls_self_signed_cert.placeholder[0].cert_pem
}

# Trust store for ALB mTLS - uploads each partner root CA to S3 plus a
# concatenated _bundle.pem that ALB references. When `partner_cas` is empty,
# we generate a self-signed throwaway CA so the trust store can still be
# created (ALB requires a non-empty bundle). Replace the placeholder by
# populating `trusted_partner_cas` in terraform.tfvars as partners onboard.
resource "tls_private_key" "placeholder" {
  count     = length(var.partner_cas) == 0 ? 1 : 0
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_self_signed_cert" "placeholder" {
  count           = length(var.partner_cas) == 0 ? 1 : 0
  private_key_pem = tls_private_key.placeholder[0].private_key_pem

  subject {
    common_name  = "${var.name}-placeholder-ca"
    organization = "TEFCA Gateway placeholder - replace with real partner CAs"
  }

  validity_period_hours = 24 * 365 * 5
  is_ca_certificate     = true
  allowed_uses          = ["cert_signing", "crl_signing"]
}

resource "aws_s3_object" "partner_ca" {
  for_each     = var.partner_cas
  bucket       = var.trust_store_bucket
  key          = "partners/${each.key}.pem"
  content      = each.value
  content_type = "application/x-pem-file"
}

resource "aws_s3_object" "bundle" {
  bucket       = var.trust_store_bucket
  key          = "partners/_bundle.pem"
  content      = local.bundle_pem
  content_type = "application/x-pem-file"
}

resource "aws_lb_trust_store" "this" {
  name                             = "${var.name}-mtls"
  ca_certificates_bundle_s3_bucket = var.trust_store_bucket
  ca_certificates_bundle_s3_key    = "partners/_bundle.pem"

  depends_on = [aws_s3_object.bundle]

  # Reload trust store whenever the bundle PEM content changes.
  lifecycle {
    replace_triggered_by = [aws_s3_object.bundle.etag]
  }
}


