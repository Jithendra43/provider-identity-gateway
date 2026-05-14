terraform {
  backend "s3" {
    # IMPORTANT: bootstrap separately. Override via -backend-config if needed.
    bucket  = "chit-terraform-state-sydata"
    key     = "Provider-Identity-gateway/envs/test/terraform.tfstate"
    region  = "us-east-1"
    encrypt = true
  }
}
