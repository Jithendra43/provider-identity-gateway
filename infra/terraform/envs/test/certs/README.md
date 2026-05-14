# Drop the E2E Test Partner CA PEM here.
#
#   1) From repo root:
#        cd test-harness/prod && ./gen-test-ca.sh
#   2) Copy the cert into this folder:
#        cp test-harness/prod/out/e2e-test-ca.pem infra/terraform/envs/test/certs/
#   3) terraform -chdir=infra/terraform/envs/test apply
#
# The matching private key (out/e2e-test-ca.key) MUST stay on the operator
# workstation — back it up to SSM SecureString at /tefca/test/e2e-test-ca-private.
# It must not be committed to git.
