# Terraform Infrastructure for Finance Tracker

This directory contains Terraform configuration for deploying the Finance Tracker application to AWS.

## Prerequisites

- Terraform >= 1.0
- AWS CLI configured with credentials
- Existing SSH key pair

## Quick Start

### 1. Initialize Terraform
```bash
terraform init
```

### 2. Create variables file
Create a `terraform.tfvars` file with your specific values:

```hcl
aws_region           = "us-east-1"
environment          = "production"
ssh_public_key       = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC..."
postgres_password    = "your-strong-password"
jwt_secret           = "your-jwt-secret-minimum-32-characters"
aws_access_key_id    = "AKIAIOSFODNN7EXAMPLE"
aws_secret_access_key = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
alert_email          = "your-email@example.com"
budget_limit         = 20
```

### 3. Plan the deployment
```bash
terraform plan -out=tfplan
```

### 4. Apply the deployment
```bash
terraform apply tfplan
```

### 5. Get outputs
```bash
terraform output
```

## Infrastructure Components

The Terraform configuration creates the following AWS resources:

### Networking
- **VPC**: Custom VPC with public and private subnets
- **Security Groups**: Configured for HTTP, HTTPS, SSH, and application ports
- **Internet Gateway**: For public internet access
- **NAT Gateway**: Optional (disabled by default for cost savings)

### Compute
- **EC2 Instance**: t2.micro (Free Tier eligible) with Ubuntu 22.04
- **Key Pair**: SSH access configuration
- **Elastic IP**: Static public IP address
- **IAM Role**: Instance profile with necessary permissions

### Storage
- **S3 Buckets**: 
  - Uploads bucket for file storage
  - Backups bucket with lifecycle policies
- **EBS Volume**: 30 GB encrypted root volume

### Container Registry
- **ECR Repository**: Private Docker registry
- **Lifecycle Policy**: Automatic image cleanup

### Monitoring
- **CloudWatch**: Metrics, logs, and dashboards
- **CloudWatch Agent**: EC2 monitoring
- **SNS**: Alert notifications
- **Budgets**: Cost monitoring and alerts

## Cost Optimization

### Free Tier Usage
- EC2 t2.micro: 750 hours/month
- S3: 5 GB storage
- CloudWatch: 10 custom metrics, 5 GB logs

### Cost-Saving Features
- NAT Gateway disabled by default
- Single NAT Gateway if enabled
- Automated resource cleanup
- Budget alerts at 80% threshold

## Outputs

After deployment, Terraform outputs important information:

```bash
terraform output ec2_public_ip
terraform output ecr_repository_url
terraform output s3_upload_bucket_name
terraform output application_url
```

## Customization

### Change Instance Type
```hcl
instance_type = "t3.small"  # or any other supported type
```

### Enable NAT Gateway
```hcl
enable_nat_gateway = true
```

### Adjust Budget
```hcl
budget_limit = 50  # Increase to $50/month
```

## Cleanup

To destroy all resources:
```bash
terraform destroy
```

## Security Notes

- SSH key is stored in Terraform state (use remote state in production)
- Secrets are marked as sensitive
- Use AWS Secrets Manager for production deployments
- Enable MFA for AWS account

## Troubleshooting

### Terraform State Lock
If you encounter state lock issues:
```bash
terraform force-unlock <LOCK_ID>
```

### Resource Creation Failures
Check AWS service quotas and region availability.

### SSH Connection Issues
Verify security group allows SSH from your IP.

## Next Steps

1. Deploy infrastructure with Terraform
2. Configure CI/CD pipeline with GitHub Actions
3. Deploy application using the provided workflow
4. Set up monitoring and alerts
5. Configure domain and SSL certificates

## Support

For issues with this Terraform configuration:
- Check Terraform documentation
- Review AWS CloudTrail logs
- Consult AWS support resources
