# TEFCA Gateway — TEST environment tfvars
# ---------------------------------------------------------------------------- 
# This is a dedicated, prod-shaped E2E test environment isolated from prod by:
#   - separate name_prefix      → all AWS resources prefixed "provider-identity-gw-test"
#   - separate hostname         → provider-identity-gw-test.c-hit.ai
#   - separate VPC CIDR         → 10.41.0.0/16 (no peering with prod's 10.40.0.0/16)
#   - separate state file key   → envs/test/terraform.tfstate (see backend.tf)
#   - the in-app test IdP /token endpoint is enabled here (k8s configmap)
#   - the E2E Test Partner CA is registered as a trust anchor below
# ----------------------------------------------------------------------------

aws_region  = "us-east-1"
name_prefix = "provider-identity-gw"   # module suffixes append "-test"
cost_center = "tefca-test"

gateway_domain = "provider-identity-gw-test.c-hit.ai"
admin_domain   = "provider-identity-gw-test.c-hit.ai"

image_tag = "latest"

# ----------------------------------------------------------------------------
# Trust store — register the E2E Test Partner CA produced by
#   test-harness/prod/gen-test-ca.sh
# Drop the file at:  infra/terraform/envs/test/certs/e2e-test-ca.pem
# ----------------------------------------------------------------------------
trusted_partner_cas = {
  e2e_test_ca = file("certs/e2e-test-ca.pem")
}

# In-app test IdP endpoints (loopback). The gateway's resource server validates
# JWTs against these. Matches MockIdpController defaults; flagged on via the
# configmap OAUTH2_*  / TEFCA_TEST_IDP_TOKEN_ENDPOINT_ENABLED overrides.
jwt_issuer_uri  = "http://localhost:8080"
jwt_jwk_set_uri = "http://127.0.0.1:8080/oauth2/.well-known/jwks.json"
jwt_audience    = "tefca-gateway"

github_org    = "C-HIT"
github_repo   = "C-HIT-Provider-Identity-Gateway"
github_branch = "main"

# ----------------------------------------------------------------------------
# Right-sized for test (cheaper than prod)
# ----------------------------------------------------------------------------
vpc_cidr            = "10.41.0.0/16"
availability_zones  = ["us-east-1a", "us-east-1b"]
enable_nat_instance = true
nat_instance_type   = "t4g.nano"

rds_instance_class        = "db.t4g.micro"
rds_multi_az              = false
rds_backup_retention_days = 7
rds_deletion_protection   = false

eks_cluster_version = "1.35"
redis_node_type     = "cache.t4g.micro"

audit_retention_days = 7
alb_log_prefix       = "alb"
log_retention_days   = 7

enable_waf = false  # cost-min for test; flip to true for parity testing

# Real ACM cert + Route53 record for gateway_domain expected.
use_self_signed_server_cert = false
