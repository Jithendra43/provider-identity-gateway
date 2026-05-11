terraform {
  backend "s3" {
    # Bootstrap separately; values must be supplied via -backend-config
    bucket = "chit-terraform-state-sydata"
    key    = "Provider-Identity-gateway/envs/prod/terraform.tfstate"
    region = "us-east-1"
    # dynamodb_table = "tefca-gw-tflock"
    encrypt = true
    # kms_key_id     = "alias/tefca-gw-tfstate"
  }
}
