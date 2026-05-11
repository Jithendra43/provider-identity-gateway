1. Each module must have variables.tf and outputs.tf
2. All the defaults should be defined in the terraform.tfvars
3. modules must be wiredup with the envs/prod properly
4. Security groups must go to terraform/modules/securitygroups.tf
5. RDS, Redis, Eks and other secrets must go to terraform/modules/secrets.tf
6. No duplication of resources