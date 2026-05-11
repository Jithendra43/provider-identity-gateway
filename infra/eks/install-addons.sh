#!/usr/bin/env bash
# Run once after the first successful `terraform apply` that created the EKS cluster.
# Requires: helm 3.x, kubectl, aws CLI, and credentials that can modify the cluster.
#
# Usage (from repo root, after exporting TF outputs or setting vars manually):
#   export CLUSTER_NAME=tefca-gw-prod
#   export AWS_REGION=us-east-1
#   export VPC_ID=vpc-xxxxxxxx
#   export LBC_ROLE_ARN=arn:aws:iam::ACCOUNT:role/tefca-gw-prod-aws-lbc
#   export ESO_ROLE_ARN=arn:aws:iam::ACCOUNT:role/tefca-gw-prod-external-secrets
#   ./infra/eks/install-addons.sh

set -euo pipefail

: "${CLUSTER_NAME:?}"
: "${AWS_REGION:?}"
: "${VPC_ID:?}"
: "${LBC_ROLE_ARN:?}"
: "${ESO_ROLE_ARN:?}"

aws eks update-kubeconfig --name "$CLUSTER_NAME" --region "$AWS_REGION"

echo "Ensuring CoreDNS is schedulable on Fargate..."
# Force CoreDNS to Fargate compute type for Fargate-only clusters.
kubectl patch deployment coredns -n kube-system --type='merge' \
  -p='{"spec":{"template":{"metadata":{"annotations":{"eks.amazonaws.com/compute-type":"fargate"}}}}}' \
  >/dev/null 2>&1 || true

COREDNS_COMPUTE_TYPE="$(kubectl get deployment coredns -n kube-system -o jsonpath='{.spec.template.metadata.annotations.eks\.amazonaws\.com/compute-type}' 2>/dev/null || true)"
if [ "$COREDNS_COMPUTE_TYPE" = "ec2" ]; then
  echo "ERROR: CoreDNS is still pinned to EC2 compute type." >&2
  kubectl get deployment coredns -n kube-system -o yaml >&2 || true
  exit 1
fi
echo "CoreDNS compute type: ${COREDNS_COMPUTE_TYPE:-unset}"

kubectl rollout restart deployment/coredns -n kube-system >/dev/null 2>&1 || true
if ! kubectl rollout status deployment/coredns -n kube-system --timeout=300s; then
  echo "ERROR: CoreDNS is not ready; add-ons that rely on DNS (including external-secrets) will fail." >&2
  kubectl get pods -n kube-system -l k8s-app=kube-dns -o wide >&2 || true
  kubectl describe pod -n kube-system -l k8s-app=kube-dns >&2 || true
  exit 1
fi

helm repo add eks https://aws.github.io/eks-charts --force-update
helm repo add external-secrets https://charts.external-secrets.io --force-update
helm repo update

TMP_LBC=$(mktemp)
TMP_ESO=$(mktemp)
cleanup() { rm -f "$TMP_LBC" "$TMP_ESO"; }
trap cleanup EXIT

cat >"$TMP_LBC" <<YAML
clusterName: ${CLUSTER_NAME}
region: ${AWS_REGION}
vpcId: ${VPC_ID}
image:
  repository: 602401143452.dkr.ecr.${AWS_REGION}.amazonaws.com/amazon/aws-load-balancer-controller
serviceAccount:
  create: true
  name: aws-load-balancer-controller
  annotations:
    eks.amazonaws.com/role-arn: ${LBC_ROLE_ARN}
YAML

helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system --wait --atomic \
  --version 1.7.1 \
  -f "$TMP_LBC"

cat >"$TMP_ESO" <<YAML
installCRDs: true
webhook:
  create: false
certController:
  create: false
serviceAccount:
  create: true
  name: external-secrets
  annotations:
    eks.amazonaws.com/role-arn: ${ESO_ROLE_ARN}
YAML

helm upgrade --install external-secrets external-secrets/external-secrets \
  -n external-secrets --create-namespace \
  --cleanup-on-fail \
  --version 0.9.19 \
  -f "$TMP_ESO"

echo "Add-ons installed. Apply Kubernetes manifests from infra/k8s/ (see README)."
