# =============================================================================
# AWS Load Balancer Controller
# Version pinned to match infra/terraform/k8s/alb-controller.tf (1.7.1).
# Image pulled from the regional ECR mirror to avoid Docker Hub rate limits.
# =============================================================================

resource "helm_release" "alb_controller" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  namespace  = "kube-system"
  version    = "1.7.1"

  wait             = true
  atomic           = true
  cleanup_on_fail  = true
  timeout          = 600

  set {
    name  = "clusterName"
    value = local.cluster_name
  }
  set {
    name  = "region"
    value = var.aws_region
  }
  set {
    name  = "vpcId"
    value = local.vpc_id
  }
  set {
    name  = "image.repository"
    value = "602401143452.dkr.ecr.${var.aws_region}.amazonaws.com/amazon/aws-load-balancer-controller"
  }
  set {
    name  = "serviceAccount.create"
    value = "true"
  }
  set {
    name  = "serviceAccount.name"
    value = "aws-load-balancer-controller"
  }
  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = local.lbc_role_arn
  }

  depends_on = [null_resource.helm_repos]
}
