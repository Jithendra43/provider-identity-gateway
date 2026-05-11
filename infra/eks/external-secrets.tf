# =============================================================================
# External Secrets Operator
# Version pinned to match infra/terraform/k8s/external-secrets.tf (0.9.19).
# Webhook and cert-controller disabled — managed separately via kubectl manifests.
# =============================================================================

resource "helm_release" "external_secrets" {
  name             = "external-secrets"
  repository       = "https://charts.external-secrets.io"
  chart            = "external-secrets"
  namespace        = "external-secrets"
  version          = "0.9.19"

  create_namespace = true
  wait             = false
  atomic           = false
  cleanup_on_fail  = true
  timeout          = 600

  set {
    name  = "installCRDs"
    value = "true"
  }

  # Disable admission webhook and cert-controller — avoids circular dependency
  # during initial cluster bootstrapping where no webhooks exist yet.
  set {
    name  = "webhook.create"
    value = "false"
  }
  set {
    name  = "certController.create"
    value = "false"
  }

  set {
    name  = "serviceAccount.create"
    value = "true"
  }
  set {
    name  = "serviceAccount.name"
    value = "external-secrets"
  }
  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = local.external_secrets_role_arn
  }

  depends_on = [null_resource.helm_repos]
}
