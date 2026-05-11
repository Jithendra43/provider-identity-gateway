#!/usr/bin/env bash
# =============================================================================
# TEFCA Gateway — bootstrap remote Terraform state + DynamoDB lock + HMAC secret
# Idempotent. Safe to re-run.
#
# Usage:
#   AWS_ACCESS_KEY_ID=...  AWS_SECRET_ACCESS_KEY=...  AWS_DEFAULT_REGION=us-east-1 \
#       ./infra/bootstrap.sh
#
# Outputs:
#   * S3 state bucket        : tefca-gw-tfstate-<accountId>
#   * DynamoDB lock table    : tefca-gw-tflock
#   * Generated backend.hcl  : infra/terraform/envs/prod/backend.hcl
#   * HMAC secret printed once — save it to your password manager and feed it
#     to Terraform via:  export TF_VAR_hmac_secret_initial=<value>
# =============================================================================
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-us-east-1}"
LOCK_TABLE="${LOCK_TABLE:-tefca-gw-tflock}"
export AWS_PAGER=""

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
BUCKET="${BUCKET:-tefca-gw-tfstate-${ACCOUNT_ID}}"

echo "Account : ${ACCOUNT_ID}"
echo "Region  : ${REGION}"
echo "Bucket  : ${BUCKET}"
echo "Table   : ${LOCK_TABLE}"
echo

# ---- 1. State bucket (idempotent) ------------------------------------------
if aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null; then
  echo "✔ Bucket exists"
else
  if [ "$REGION" = "us-east-1" ]; then
    aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" >/dev/null
  else
    aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" \
      --create-bucket-configuration "LocationConstraint=$REGION" >/dev/null
  fi
  echo "✔ Bucket created"
fi

aws s3api put-public-access-block --bucket "$BUCKET" \
  --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
aws s3api put-bucket-versioning --bucket "$BUCKET" \
  --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket "$BUCKET" \
  --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"},"BucketKeyEnabled":true}]}'
echo "✔ Bucket hardened (block-public, versioning, SSE)"

# ---- 2. Lock table (idempotent) --------------------------------------------
if aws dynamodb describe-table --table-name "$LOCK_TABLE" --region "$REGION" >/dev/null 2>&1; then
  echo "✔ Lock table exists"
else
  aws dynamodb create-table \
    --table-name "$LOCK_TABLE" \
    --attribute-definitions AttributeName=LockID,AttributeType=S \
    --key-schema AttributeName=LockID,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region "$REGION" >/dev/null
  aws dynamodb wait table-exists --table-name "$LOCK_TABLE" --region "$REGION"
  echo "✔ Lock table created"
fi

# ---- 3. Write backend.hcl --------------------------------------------------
BACKEND_DIR="$(cd "$(dirname "$0")/terraform/envs/prod" && pwd)"
BACKEND_FILE="$BACKEND_DIR/backend.hcl"
cat > "$BACKEND_FILE" <<EOF
bucket         = "$BUCKET"
key            = "envs/prod/terraform.tfstate"
region         = "$REGION"
dynamodb_table = "$LOCK_TABLE"
encrypt        = true
EOF
echo "✔ Wrote $BACKEND_FILE"

# ---- 4. HMAC secret (only minted if not already set) -----------------------
if [ -n "${TF_VAR_hmac_secret_initial:-}" ]; then
  echo "✔ TF_VAR_hmac_secret_initial already set — using it"
else
  HMAC="$(openssl rand -hex 32)"
  echo
  echo "==========================================================="
  echo "  Generated TF_VAR_hmac_secret_initial — SAVE THIS NOW:"
  echo "  $HMAC"
  echo "  (then run:  export TF_VAR_hmac_secret_initial=$HMAC )"
  echo "==========================================================="
fi

echo
echo "Next:"
echo "  cd infra/terraform/envs/prod"
echo "  cp terraform.tfvars.example terraform.tfvars   # edit values"
echo "  terraform init -backend-config=backend.hcl"
echo "  terraform plan -out=tfplan"
echo "  terraform apply tfplan"
