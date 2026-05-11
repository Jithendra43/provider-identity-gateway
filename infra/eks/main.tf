# =============================================================================
# TEFCA Gateway — EKS Add-ons layer
# Apply AFTER infra/terraform/envs/prod has been applied and the EKS cluster exists.
#
# Usage:
#   cd infra/eks/
#   terraform init
#   terraform apply
# =============================================================================

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.11"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.27"
    }
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
  }

  backend "s3" {
    bucket  = "chit-terraform-state-sydata"
    key     = "Provider-Identity-gateway/eks/addons/terraform.tfstate"
    region  = "us-east-1"
    encrypt = true
  }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Application = "tefca-gateway"
      Environment = "prod"
      Compliance  = "HIPAA-TEFCA"
      ManagedBy   = "Terraform"
    }
  }
}

# =============================================================================
# READ INFRA STATE
# Reads outputs from infra/terraform/envs/prod (EKS cluster, IRSA ARNs, VPC ID)
# =============================================================================

data "terraform_remote_state" "infra" {
  backend = "s3"
  config = {
    bucket  = "chit-terraform-state-sydata"
    key     = "Provider-Identity-gateway/envs/prod/terraform.tfstate"
    region  = "us-east-1"
  }
}

# =============================================================================
# LOCALS — resolve values from remote state with variable fallbacks
# =============================================================================

locals {
  cluster_name              = try(data.terraform_remote_state.infra.outputs.eks_cluster_name, var.cluster_name)
  lbc_role_arn              = try(data.terraform_remote_state.infra.outputs.eks_irsa_lbc_role_arn, var.lbc_role_arn)
  external_secrets_role_arn = try(data.terraform_remote_state.infra.outputs.eks_irsa_external_secrets_role_arn, var.external_secrets_role_arn)
  vpc_id                    = try(data.terraform_remote_state.infra.outputs.vpc_id, var.vpc_id)
}

# =============================================================================
# EKS CLUSTER DATA SOURCES
# =============================================================================

data "aws_eks_cluster" "this" {
  name = local.cluster_name
}

data "aws_eks_cluster_auth" "this" {
  name = local.cluster_name
}

# =============================================================================
# HELM PROVIDER
# Authenticates directly to the EKS API — no local kubeconfig required.
# =============================================================================

provider "helm" {
  kubernetes {
    host                   = data.aws_eks_cluster.this.endpoint
    cluster_ca_certificate = base64decode(data.aws_eks_cluster.this.certificate_authority[0].data)
    token                  = data.aws_eks_cluster_auth.this.token
  }
}

provider "kubernetes" {
  host                   = data.aws_eks_cluster.this.endpoint
  cluster_ca_certificate = base64decode(data.aws_eks_cluster.this.certificate_authority[0].data)
  token                  = data.aws_eks_cluster_auth.this.token
}

# =============================================================================
# UPDATE LOCAL KUBECONFIG
# Convenience: keeps ~/.kube/config in sync after apply.
# =============================================================================

resource "null_resource" "update_kubeconfig" {
  provisioner "local-exec" {
    command = "aws eks update-kubeconfig --region ${var.aws_region} --name ${local.cluster_name}"
  }

  triggers = {
    cluster_name = local.cluster_name
  }
}

# Ensure CoreDNS is schedulable on Fargate-only clusters.
resource "null_resource" "patch_coredns_for_fargate" {
  provisioner "local-exec" {
    command = join(" && ", [
      "kubectl annotate deployment coredns -n kube-system eks.amazonaws.com/compute-type=fargate --overwrite",
      "kubectl rollout restart deployment/coredns -n kube-system",
      "kubectl rollout status deployment/coredns -n kube-system --timeout=300s"
    ])
  }

  triggers = {
    cluster_name = local.cluster_name
  }

  depends_on = [null_resource.update_kubeconfig]
}

# =============================================================================
# HELM REPO SETUP
# Adds and updates the Helm repositories required by helm_release resources.
# Necessary on Windows where the Helm provider relies on the local CLI cache.
# =============================================================================
resource "null_resource" "helm_repos" {
  provisioner "local-exec" {
    command = join(" && ", [
      "helm repo add eks https://aws.github.io/eks-charts",
      "helm repo add external-secrets https://charts.external-secrets.io",
      "helm repo update"
    ])
  }

  triggers = {
    always = timestamp()
  }

  depends_on = [null_resource.patch_coredns_for_fargate]
}

# =============================================================================
# FARGATE LOGGING — CloudWatch for Fargate Container Insights
# Creates aws-observability namespace and Fluent Bit configuration for EKS Fargate
# Logs are sent to CloudWatch Logs in /aws/eks/<cluster-name>
# =============================================================================

resource "kubernetes_namespace" "aws_observability" {
  metadata {
    name = "aws-observability"
    labels = {
      "aws-observability" = "enabled"
    }
  }

  depends_on = [null_resource.helm_repos]
}

resource "kubernetes_config_map" "aws_logging" {
  metadata {
    name      = "aws-logging"
    namespace = kubernetes_namespace.aws_observability.metadata[0].name
  }

  data = {
    "output.conf" = <<-EOT
      [OUTPUT]
          Name cloudwatch_logs
          Match *
          region ${var.aws_region}
          log_group_name /aws/eks/${local.cluster_name}
          log_stream_prefix from-fluent-bit-
          auto_create_group true
    EOT
  }

  depends_on = [kubernetes_namespace.aws_observability]
}
