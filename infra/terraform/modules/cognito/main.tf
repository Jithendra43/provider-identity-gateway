resource "aws_cognito_user_pool" "admin" {
  name = var.name

  password_policy {
    minimum_length    = 14
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = true
  }

  mfa_configuration = "ON"
  software_token_mfa_configuration { enabled = true }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  admin_create_user_config { allow_admin_create_user_only = true }

  user_pool_add_ons { advanced_security_mode = "ENFORCED" }

  schema {
    name                = "email"
    attribute_data_type = "String"
    required            = true
    mutable             = true
  }
}

resource "aws_cognito_user_pool_domain" "admin" {
  domain       = var.name
  user_pool_id = aws_cognito_user_pool.admin.id
}

resource "aws_cognito_user_pool_client" "admin" {
  name                                 = "${var.name}-client"
  user_pool_id                         = aws_cognito_user_pool.admin.id
  generate_secret                      = true
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  callback_urls                        = var.callback_urls
  logout_urls                          = var.logout_urls
  supported_identity_providers         = ["COGNITO"]
  prevent_user_existence_errors        = "ENABLED"
  enable_token_revocation              = true
  access_token_validity                = 60
  id_token_validity                    = 60
  refresh_token_validity               = 24
  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "hours"
  }
}

data "aws_region" "here" {}
