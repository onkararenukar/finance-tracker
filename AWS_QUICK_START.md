# AWS Deployment Quick Start Guide

This guide provides a quick reference for deploying the Finance Tracker application to AWS using the comprehensive deployment materials provided.

## 📋 Prerequisites Checklist

- [ ] AWS Account with free tier enabled
- [ ] GitHub Account (for CI/CD)
- [ ] AWS CLI installed and configured
- [ ] Docker installed locally
- [ ] Git installed
- [ ] SSH key pair generated

## 🚀 Quick Deployment Steps

### Step 1: Set Up AWS Infrastructure (5 minutes)

#### Option A: Using Terraform (Recommended)
```bash
cd terraform
terraform init
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your values
terraform plan -out=tfplan
terraform apply tfplan
```

#### Option B: Manual Setup
Follow the detailed steps in `AWS_DEPLOYMENT.md` sections 1-5.

### Step 2: Configure GitHub Secrets (3 minutes)

Add these secrets to your GitHub repository:

| Secret Name | Value |
|-------------|-------|
| `AWS_ACCESS_KEY_ID` | Your AWS Access Key |
| `AWS_SECRET_ACCESS_KEY` | Your AWS Secret Key |
| `EC2_PRIVATE_KEY` | Your EC2 SSH private key |
| `EC2_HOST` | Your EC2 public IP/DNS |
| `POSTGRES_PASSWORD` | Strong database password |
| `JWT_SECRET` | Strong JWT secret (32+ chars) |
| `S3_BUCKET_NAME` | Your S3 bucket name |
| `EC2_PUBLIC_IP` | Your EC2 public IP |

### Step 3: Push to GitHub (1 minute)

```bash
git add .
git commit -m "Add AWS deployment configuration"
git push origin main
```

### Step 4: Automatic Deployment (5 minutes)

The GitHub Actions workflow will automatically:
1. Run tests
2. Build Docker images
3. Push to ECR
4. Deploy to EC2
5. Run health checks

### Step 5: Verify Deployment (2 minutes)

```bash
# Check application health
curl http://<YOUR_EC2_IP>:8080/actuator/health

# Test authentication
curl -X POST http://<YOUR_EC2_IP>:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

## 📊 Cost Summary

### Free Tier Monthly Costs
- **EC2 t2.micro**: $0 (750 hours/month free)
- **S3 Storage**: $0 (5 GB free)
- **CloudWatch**: $0 (10 metrics, 5 GB logs free)
- **Data Transfer**: $0 (100 GB/month free)

### Potential Additional Costs
- **EIP**: ~$3.50/month (can be avoided with public IP)
- **Data overage**: ~$0.09/GB (after 100 GB)
- **S3 overage**: ~$0.023/GB (after 5 GB)

**Estimated Total**: $0-10/month for typical usage

## 🔧 Manual Deployment Alternative

If you prefer manual deployment:

```bash
# SSH into EC2
ssh -i finance-tracker-key.pem ubuntu@<EC2_PUBLIC_IP>

# Clone repository
git clone <YOUR_REPO> finance-tracker
cd finance-tracker

# Set up environment
cp .env.example .env
# Edit .env with your values

# Deploy
docker-compose -f docker-compose.prod.yml up -d
```

## 📱 Access Points

After deployment:

- **API Gateway**: http://<EC2_IP>:8080
- **Swagger UI**: http://<EC2_IP>:8080/swagger-ui/index.html
- **Kafka UI**: http://<EC2_IP>:8090
- **Health Check**: http://<EC2_IP>:8080/actuator/health

## 🔐 Default Credentials

- **Username**: `admin`
- **Password**: `admin123`
- **Role**: ADMIN

## 📈 Monitoring

Access monitoring dashboards:

- **CloudWatch**: https://console.aws.amazon.com/cloudwatch/
- **Terraform Output**: `terraform output cloudwatch_dashboard_url`
- **Budget Alerts**: Configured in AWS Budgets

## 🔄 Rollback Procedure

If deployment fails:

```bash
# Automatic rollback (if enabled in workflow)
# Manual rollback:
cd /home/ubuntu/finance-tracker
git checkout <previous-commit>
docker-compose down
docker-compose up -d
```

## 🆘 Troubleshooting

### Common Issues

**1. Deployment fails**
- Check GitHub Actions logs
- Verify AWS credentials
- Ensure EC2 instance is running

**2. Can't access application**
- Check security group rules
- Verify EC2 public IP
- Check application logs: `docker-compose logs`

**3. Database connection issues**
- Verify PostgreSQL container is running
- Check database credentials in .env
- Review PostgreSQL logs

**4. High CPU usage**
- Check application logs for performance issues
- Consider upgrading instance type
- Implement caching

## 📚 Documentation Reference

- **Full Deployment Guide**: `AWS_DEPLOYMENT.md`
- **Terraform Configuration**: `terraform/`
- **CI/CD Pipeline**: `.github/workflows/deploy.yml`
- **Production Docker Compose**: `docker-compose.prod.yml`
- **Environment Variables**: `.env.example`

## 🎯 Next Steps

1. **Customize domain**: Purchase domain and configure DNS
2. **Enable SSL**: Configure Let's Encrypt for HTTPS
3. **Set up monitoring**: Configure custom CloudWatch alerts
4. **Implement scaling**: Add auto-scaling for high availability
5. **Optimize costs**: Review and adjust resources as needed

## 📞 Support

For issues with:
- **AWS Infrastructure**: Check AWS Console and CloudTrail logs
- **Application**: Check application logs and GitHub Actions logs
- **Deployment**: Review CI/CD pipeline logs

## 🔒 Security Notes

- Change default passwords immediately
- Use strong JWT secrets
- Enable MFA for AWS account
- Regularly rotate access keys
- Monitor AWS Budgets for unexpected costs

---

**Estimated Total Setup Time**: 15-20 minutes

**Estimated Monthly Cost**: $0-10 (Free Tier)

**Support Level**: Self-service with comprehensive documentation
