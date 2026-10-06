# AWS Deployment Guide - Finance Tracker

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [AWS Free Tier Overview](#aws-free-tier-overview)
3. [Infrastructure Setup](#infrastructure-setup)
4. [Application Deployment](#application-deployment)
5. [CI/CD Pipeline Setup](#cicd-pipeline-setup)
6. [Monitoring and Maintenance](#monitoring-and-maintenance)
7. [Cost Management](#cost-management)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Accounts and Tools
- **AWS Account** with free tier enabled
- **GitHub Account** (for CI/CD)
- **Local Tools:**
  - AWS CLI v2
  - Docker
  - Git
  - kubectl (optional, for EKS)

### Install AWS CLI
```bash
# On macOS
brew install awscli

# On Linux
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Configure AWS CLI
aws configure
# Enter your AWS Access Key ID and Secret Access Key
# Choose us-east-1 (N. Virginia) or other preferred region
```

### Verify AWS Setup
```bash
aws sts get-caller-identity
# Should return your AWS account information
```

---

## AWS Free Tier Overview

### Free Tier Services Used
| Service | Free Tier Limit | Monthly Cost (after free tier) |
|---------|----------------|-------------------------------|
| **EC2** | 750 hours/month t2.micro/t3.micro | ~$8-15/month |
| **ECS Fargate** | No free tier (use EC2 instead) | ~$20-50/month |
| **ECR** | 500 MB-month storage | ~$0.10/GB-month |
| **RDS PostgreSQL** | No free tier (use containerized DB) | ~$15-50/month |
| **S3** | 5 GB standard storage | ~$0.023/GB |
| **CloudWatch** | 10 custom metrics, 5 GB logs | ~$0.50/GB logs |
| **Elastic Load Balancer** | 750 hours/month | ~$0.0225/hour |
| **NAT Gateway** | No free tier | ~$0.045/hour + $0.045/GB |

### **Recommended Free Tier Architecture**
- **1x EC2 t2.micro** for application hosting
- **Docker Compose on EC2** for multi-service deployment
- **S3** for file storage and backups
- **CloudWatch** for monitoring and logging
- **Route 53** (optional) for DNS management

### **Cost-Saving Alternatives**
- Use containerized PostgreSQL on EC2 instead of RDS
- Use AWS Free Tier EC2 instance
- Implement proper cleanup strategies
- Monitor costs with AWS Budgets

---

## Infrastructure Setup

### Step 1: Create VPC and Networking

#### 1.1 Access VPC Dashboard
1. Log in to AWS Console
2. Navigate to **VPC** dashboard
3. Click **"Start VPC Wizard"**

#### 1.2 Create VPC
![VPC Dashboard Screenshot Description: AWS VPC dashboard showing "Start VPC Wizard" button highlighted]

**VPC Configuration:**
- **Name tag:** `finance-tracker-vpc`
- **IPv4 CIDR block:** `10.0.0.0/16`
- **Tenancy:** Default

#### 1.3 Create Subnets
Create two public subnets for high availability:

**Subnet 1:**
- **Name:** `finance-tracker-public-subnet-1a`
- **Availability Zone:** us-east-1a
- **CIDR block:** `10.0.1.0/24`

**Subnet 2:**
- **Name:** `finance-tracker-public-subnet-1b`
- **Availability Zone:** us-east-1b
- **CIDR block:** `10.0.2.0/24`

#### 1.4 Create Internet Gateway
1. Navigate to **Internet Gateways**
2. Click **"Create internet gateway"**
3. **Name:** `finance-tracker-igw`
4. Attach to your VPC

#### 1.5 Configure Route Tables
1. Navigate to **Route Tables**
2. Create route table: `finance-tracker-public-rt`
3. Add route: `0.0.0.0/0` → Internet Gateway
4. Associate with both public subnets

#### 1.6 Create Security Group
1. Navigate to **Security Groups**
2. Click **"Create security group"**
3. **Configuration:**
   - **Name:** `finance-tracker-sg`
   - **Description:** Security group for Finance Tracker
   - **VPC:** Select your VPC

**Inbound Rules:**
| Type | Protocol | Port Range | Source | Description |
|------|----------|-------------|--------|-------------|
| SSH | TCP | 22 | Your IP (0.0.0.0/0 for testing) | SSH access |
| HTTP | TCP | 80 | 0.0.0.0/0 | Web access |
| HTTPS | TCP | 443 | 0.0.0.0/0 | Secure web access |
| Custom | TCP | 8080-8084 | 0.0.0.0/0 | Application ports |

### Step 2: Create EC2 Instance

#### 2.1 Launch EC2 Instance
1. Navigate to **EC2 Dashboard**
2. Click **"Launch Instance"**
3. **Name:** `finance-tracker-server`

#### 2.2 Choose AMI
- **AMI:** Ubuntu Server 22.04 LTS (HVM)
- **Architecture:** x86_64
- **Instance Type:** t2.micro (Free Tier eligible)

![EC2 AMI Selection Screenshot Description: EC2 launch instance wizard showing Ubuntu 22.04 AMI selected]

#### 2.3 Configure Instance Details
- **Network:** Your VPC
- **Subnet:** Public subnet 1a
- **Auto-assign Public IP:** Enable
- **Security Group:** Select `finance-tracker-sg`

#### 2.4 Add Storage
- **Root volume:** 30 GB GP3 (Free tier: 30 GB)
- **Delete on termination:** Enable

#### 2.5 Configure Key Pair
1. Click **"Create new key pair"**
2. **Name:** `finance-tracker-key`
3. **Type:** RSA
4. **Format:** .pem
5. **Download and save securely**

![Key Pair Creation Screenshot Description: EC2 key pair creation dialog showing download option]

#### 2.6 Launch Instance
Click **"Launch Instance"** and wait for instance to be ready.

### Step 3: Create ECR Repository

#### 3.1 Access ECR
1. Navigate to **Elastic Container Registry**
2. Click **"Create repository"**

#### 3.2 Create Repository
- **Repository name:** `finance-tracker`
- **Visibility:** Private
- **Image tag mutability:** Mutable

![ECR Repository Creation Screenshot Description: ECR create repository dialog]

#### 3.3 Repository Policy
Add lifecycle policy to manage image storage:

```json
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Keep last 10 images",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": 10
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}
```

### Step 4: Create S3 Buckets

#### 4.1 Create Upload Bucket
1. Navigate to **S3**
2. Click **"Create bucket"**
3. **Bucket name:** `finance-tracker-uploads` (unique globally)
4. **Region:** Same as EC2
5. **Block Public Access:** Disable (for public access) or configure properly

#### 4.2 Create Backup Bucket
- **Bucket name:** `finance-tracker-backups`
- **Versioning:** Enable
- **Encryption:** Enable

![S3 Bucket Creation Screenshot Description: S3 create bucket dialog showing configuration options]

### Step 5: Set Up IAM Roles

#### 5.1 Create IAM Role for EC2
1. Navigate to **IAM** → **Roles**
2. Click **"Create role"**
3. **Trusted entity:** AWS Service → EC2
4. **Permissions:**
   - `AmazonEC2ContainerRegistryReadOnly`
   - `AmazonS3FullAccess` (restrict in production)
   - `CloudWatchLogsFullAccess` (restrict in production)

#### 5.2 Attach Role to EC2
1. Select your EC2 instance
2. Click **Actions** → **Security** → **Modify IAM role**
3. Attach the created role

---

## Application Deployment

### Step 1: Connect to EC2 Instance

```bash
# Connect to EC2 instance
ssh -i finance-tracker-key.pem ubuntu@<YOUR_EC2_PUBLIC_IP>

# Or use AWS Systems Manager Session Manager (recommended)
aws ssm start-session --target i-<instance-id>
```

### Step 2: Install Docker and Docker Compose

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add user to docker group
sudo usermod -aG docker ubuntu

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Verify installation
docker --version
docker-compose --version
```

### Step 3: Configure Application

#### 3.1 Clone Repository
```bash
cd /home/ubuntu
git clone <YOUR_REPOSITORY_URL>
cd finance-tracker
```

#### 3.2 Update docker-compose.yml for Production
```yaml
version: '3.9'

services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: finance-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: financetracker
      POSTGRES_USER: finance_app
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./infra/postgres/init-extensions.sql:/docker-entrypoint-initdb.d/init-extensions.sql
    networks:
      - finance-net

  kafka:
    image: apache/kafka:3.8.0
    container_name: finance-kafka
    restart: unless-stopped
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://<EC2_PUBLIC_IP>:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    networks:
      - finance-net

  # ... other services with updated configurations

networks:
  finance-net:
    driver: bridge

volumes:
  postgres_data:
```

#### 3.3 Create Environment File
```bash
cat > .env << EOF
POSTGRES_PASSWORD=<STRONG_PASSWORD>
JWT_SECRET=<STRONG_JWT_SECRET>
AWS_ACCESS_KEY_ID=<YOUR_AWS_ACCESS_KEY>
AWS_SECRET_ACCESS_KEY=<YOUR_AWS_SECRET_KEY>
AWS_REGION=us-east-1
S3_BUCKET_NAME=finance-tracker-uploads
EOF
```

### Step 4: Build and Deploy Application

#### 4.1 Build Application
```bash
# Build all services
mvn clean package -DskipTests

# Build Docker images
docker-compose build
```

#### 4.2 Push to ECR (Optional)
```bash
# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <YOUR_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Tag images
docker tag finance-tracker-api-gateway:latest <YOUR_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/finance-tracker:api-gateway-latest
docker tag finance-tracker-ingestion-service:latest <YOUR_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/finance-tracker:ingestion-latest
# ... tag other services

# Push to ECR
docker push <YOUR_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/finance-tracker:api-gateway-latest
docker push <YOUR_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/finance-tracker:ingestion-latest
# ... push other services
```

#### 4.3 Start Application
```bash
# Start all services
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f
```

### Step 5: Configure Domain and SSL (Optional)

#### 5.1 Purchase Domain
1. Purchase domain from Route 53 or other registrar
2. Point DNS to EC2 public IP

#### 5.2 Configure SSL with Let's Encrypt
```bash
# Install Certbot
sudo apt install certbot python3-certbot-nginx -y

# Obtain SSL certificate
sudo certbot certonly --standalone -d yourdomain.com

# Configure Nginx reverse proxy
sudo apt install nginx -y
```

#### 5.3 Nginx Configuration
```nginx
server {
    listen 80;
    server_name yourdomain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## CI/CD Pipeline Setup

### Step 1: GitHub Actions Pipeline

Create `.github/workflows/deploy.yml`:

```yaml
name: Deploy to AWS

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

env:
  AWS_REGION: us-east-1
  ECR_REPOSITORY: finance-tracker
  EC2_INSTANCE_ID: <YOUR_EC2_INSTANCE_ID>

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest

    steps:
    - name: Checkout code
      uses: actions/checkout@v3

    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'

    - name: Build with Maven
      run: mvn clean package -DskipTests

    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v2
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ env.AWS_REGION }}

    - name: Login to Amazon ECR
      id: login-ecr
      uses: aws-actions/amazon-ecr-login@v1

    - name: Build and push Docker images
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        IMAGE_TAG: ${{ github.sha }}
      run: |
        # Build API Gateway
        docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:api-gateway-$IMAGE_TAG -f api-gateway/Dockerfile .
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:api-gateway-$IMAGE_TAG
        
        # Build Ingestion Service
        docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:ingestion-$IMAGE_TAG -f ingestion-service/Dockerfile .
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:ingestion-$IMAGE_TAG
        
        # Build other services similarly...

    - name: Deploy to EC2
      env:
        PRIVATE_KEY: ${{ secrets.EC2_PRIVATE_KEY }}
        EC2_HOST: ${{ secrets.EC2_HOST }}
      run: |
        echo "$PRIVATE_KEY" > private_key.pem
        chmod 600 private_key.pem
        
        # Deploy to EC2
        ssh -o StrictHostKeyChecking=no -i private_key.pem ubuntu@$EC2_HOST << 'ENDSSH'
          cd /home/ubuntu/finance-tracker
          git pull origin main
          docker-compose pull
          docker-compose up -d
        ENDSSH

    - name: Run health checks
      run: |
        sleep 30
        curl -f http://${{ secrets.EC2_HOST }}/actuator/health || exit 1
```

### Step 2: Configure GitHub Secrets

Add the following secrets in your GitHub repository:

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `AWS_ACCESS_KEY_ID` | AWS Access Key | `AKIAIOSFODNN7EXAMPLE` |
| `AWS_SECRET_ACCESS_KEY` | AWS Secret Key | `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY` |
| `EC2_PRIVATE_KEY` | EC2 SSH private key | `-----BEGIN RSA PRIVATE KEY-----...` |
| `EC2_HOST` | EC2 public IP or DNS | `ec2-xx-xx-xx-xx.compute-1.amazonaws.com` |

![GitHub Secrets Configuration Screenshot Description: GitHub repository settings showing secrets configuration]

### Step 3: AWS CodePipeline Alternative

Create `pipeline.yml` for AWS CodePipeline:

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Description: 'Finance Tracker CI/CD Pipeline'

Parameters:
  ProjectName:
    Type: String
    Default: finance-tracker

Resources:
  CodeBuildProject:
    Type: AWS::CodeBuild::Project
    Properties:
      Name: !Sub '${ProjectName}-build'
      ServiceRole: !Ref CodeBuildRole
      Artifacts:
        Type: CODEPIPELINE
      Environment:
        ComputeType: BUILD_GENERAL1_SMALL
        Image: aws/codebuild/standard:6.0
        Type: LINUX_CONTAINER
      Source:
        Type: CODEPIPELINE
      BuildSpec: |
        version: 0.2
        phases:
          install:
            runtime-versions:
              java: corretto21
          pre_build:
            commands:
              - echo Logging in to Amazon ECR...
              - aws ecr get-login-password --region $AWS_DEFAULT_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com
          build:
            commands:
              - echo Build started on `date`
              - mvn clean package -DskipTests
              - docker build -t $IMAGE_URI:$IMAGE_TAG .
              - docker tag $IMAGE_URI:$IMAGE_TAG $IMAGE_URI:latest
          post_build:
            commands:
              - echo Build completed on `date`
              - docker push $IMAGE_URI:$IMAGE_TAG
              - docker push $IMAGE_URI:latest
        env:
          parameter-store:
            AWS_ACCOUNT_ID: AWS_ACCOUNT_ID
          exported-variables:
            - IMAGE_URI
            - IMAGE_TAG

  CodePipeline:
    Type: AWS::CodePipeline::Pipeline
    Properties:
      Name: !Sub '${ProjectName}-pipeline'
      RoleArn: !Ref CodePipelineRole
      Stages:
        - Name: Source
          Actions:
            - Name: GitHub
              ActionTypeId:
                Category: Source
                Owner: ThirdParty
                Provider: GitHub
                Version: '1'
              Configuration:
                Owner: !Ref GitHubOwner
                Repo: !Ref GitHubRepo
                Branch: main
                OAuthToken: !Ref GitHubOAuthToken
              OutputArtifacts:
                - Name: SourceCode
        - Name: Build
          Actions:
            - Name: CodeBuild
              ActionTypeId:
                Category: Build
                Owner: AWS
                Provider: CodeBuild
                Version: '1'
              Configuration:
                ProjectName: !Ref CodeBuildProject
              InputArtifacts:
                - Name: SourceCode
              OutputArtifacts:
                - Name: BuildOutput
        - Name: Deploy
          Actions:
            - Name: DeployToEC2
              ActionTypeId:
                Category: Deploy
                Owner: AWS
                Provider: CodeDeploy
                Version: '1'
              Configuration:
                ApplicationName: !Ref CodeDeployApplication
                DeploymentGroupName: !Ref CodeDeployDeploymentGroup
              InputArtifacts:
                - Name: BuildOutput

  CodeBuildRole:
    Type: AWS::IAM::Role
    Properties:
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal:
              Service: codebuild.amazonaws.com
            Action: sts:AssumeRole
      Policies:
        - PolicyName: CodeBuildPolicy
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action:
                  - logs:CreateLogGroup
                  - logs:CreateLogStream
                  - logs:PutLogEvents
                Resource: arn:aws:logs:*:*:*
              - Effect: Allow
                Action:
                  - ecr:GetDownloadUrlForLayer
                  - ecr:BatchGetImage
                  - ecr:BatchCheckLayerAvailability
                Resource: !Sub 'arn:aws:ecr:${AWS::Region}:${AWS::AccountId}:repository/${ProjectName}'

  CodePipelineRole:
    Type: AWS::IAM::Role
    Properties:
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal:
              Service: codepipeline.amazonaws.com
            Action: sts:AssumeRole
      Policies:
        - PolicyName: CodePipelinePolicy
          PolicyDocument:
            Version: '2012-10-17'
            Statement:
              - Effect: Allow
                Action:
                  - codebuild:StartBuild
                  - codebuild:BatchGetBuilds
                Resource: !Sub 'arn:aws:codebuild:${AWS::Region}:${AWS::AccountId}:project/${ProjectName}-build'
              - Effect: Allow
                Action:
                  - codedeploy:CreateDeployment
                  - codedeploy:GetDeployment
                Resource: !Sub 'arn:aws:codedeploy:${AWS::Region}:${AWS::AccountId}:application:${ProjectName}'

  CodeDeployApplication:
    Type: AWS::CodeDeploy::Application
    Properties:
      ApplicationName: !Sub '${ProjectName}-app'

  CodeDeployDeploymentGroup:
    Type: AWS::CodeDeploy::DeploymentGroup
    Properties:
      ApplicationName: !Ref CodeDeployApplication
      DeploymentGroupName: !Sub '${ProjectName}-group'
      ServiceRoleArn: !Ref CodeDeployRole
      DeploymentConfigName: CodeDeployDefault.OneAtATime
      Ec2TagFilters:
        - Key: Name
          Value: finance-tracker-server
          Type: KEY_AND_VALUE

  CodeDeployRole:
    Type: AWS::IAM::Role
    Properties:
      AssumeRolePolicyDocument:
        Version: '2012-10-17'
        Statement:
          - Effect: Allow
            Principal:
              Service: codedeploy.amazonaws.com
            Action: sts:AssumeRole
      ManagedPolicyArns:
        - arn:aws:iam::aws:policy/service-role/AmazonEC2RoleforAWSCodeDeploy

Outputs:
  PipelineUrl:
    Description: CodePipeline URL
    Value: !Sub 'https://console.aws.amazon.com/codesuite/codepipeline/pipelines/${ProjectName}-pipeline/view'
```

---

## Monitoring and Maintenance

### Step 1: CloudWatch Monitoring

#### 1.1 Create CloudWatch Dashboard
1. Navigate to **CloudWatch** → **Dashboards**
2. Click **"Create dashboard"**
3. **Name:** `finance-tracker-dashboard`

#### 1.2 Add Metrics
Add the following metrics to your dashboard:

**EC2 Metrics:**
- CPU Utilization
- Memory Utilization (requires CloudWatch Agent)
- Disk Space Usage
- Network In/Out

**Application Metrics:**
- Request Count
- Response Time
- Error Rate
- Active Connections

![CloudWatch Dashboard Screenshot Description: CloudWatch dashboard showing EC2 and application metrics]

#### 1.3 Install CloudWatch Agent
```bash
# Download CloudWatch agent
wget https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb

# Install agent
sudo dpkg -i -E ./amazon-cloudwatch-agent.deb

# Configure agent
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-config-wizard

# Start agent
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -s
```

### Step 2: Log Management

#### 2.1 Configure CloudWatch Logs
```bash
# Create log group
aws logs create-log-group --log-group-name /finance-tracker/application

# Create log stream
aws logs create-log-stream --log-group-name /finance-tracker/application --log-stream-name production
```

#### 2.2 Configure Docker Logging
Update `docker-compose.yml`:
```yaml
services:
  api-gateway:
    logging:
      driver: "awslogs"
      options:
        awslogs-region: "us-east-1"
        awslogs-group: "/finance-tracker/application"
        awslogs-stream: "api-gateway"
```

### Step 3: Automated Backups

#### 3.1 Database Backups
```bash
# Create backup script
cat > /home/ubuntu/backup-db.sh << 'EOF'
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/home/ubuntu/backups"
mkdir -p $BACKUP_DIR

# Backup PostgreSQL
docker exec finance-postgres pg_dump -U finance_app financetracker > $BACKUP_DIR/financetracker_$DATE.sql

# Upload to S3
aws s3 cp $BACKUP_DIR/financetracker_$DATE.sql s3://finance-tracker-backups/

# Clean up old backups (keep last 7 days)
find $BACKUP_DIR -name "financetracker_*.sql" -mtime +7 -delete
EOF

chmod +x /home/ubuntu/backup-db.sh

# Add to crontab for daily backups
(crontab -l 2>/dev/null; echo "0 2 * * * /home/ubuntu/backup-db.sh") | crontab -
```

#### 3.2 Application Backups
```bash
# Backup uploaded files
aws s3 sync /home/ubuntu/uploads s3://finance-tracker-uploads/backups/$(date +%Y%m%d)
```

### Step 4: Health Checks

#### 4.1 Create Health Check Script
```bash
cat > /home/ubuntu/health-check.sh << 'EOF'
#!/bin/bash
# Check service health
SERVICES=("api-gateway" "ingestion-service" "parsing-service" "categorization-service" "analytics-service")

for service in "${SERVICES[@]}"; do
  if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "$service: HEALTHY"
  else
    echo "$service: UNHEALTHY"
    # Send alert (configure SNS or email)
  fi
done
EOF

chmod +x /home/ubuntu/health-check.sh

# Add to crontab for every 5 minutes
(crontab -l 2>/dev/null; echo "*/5 * * * * /home/ubuntu/health-check.sh") | crontab -
```

---

## Cost Management

### Step 1: Set Up AWS Budgets

#### 1.1 Create Budget
1. Navigate to **Billing and Cost Management** → **Budgets**
2. Click **"Create budget"**
3. **Budget type:** Cost budget
4. **Budget amount:** $20 (adjust based on your needs)
5. **Budget period:** Monthly

#### 1.2 Configure Alerts
- **Alert threshold:** 80% of budget
- **Notification email:** Your email
- **Alert type:** Actual spend

![AWS Budget Configuration Screenshot Description: AWS budget creation dialog showing alert configuration]

### Step 2: Cost Optimization

#### 2.1 Use Reserved Instances
- Purchase t2.micro reserved instances for 1-year term
- Save up to 30% compared to on-demand

#### 2.2 Implement Auto Scaling
```yaml
# Auto Scaling Group configuration
Resources:
  AutoScalingGroup:
    Type: AWS::AutoScaling::AutoScalingGroup
    Properties:
      MinSize: 1
      MaxSize: 3
      DesiredCapacity: 1
      LaunchTemplate:
        LaunchTemplateId: !Ref LaunchTemplate
        Version: '$Latest'
```

#### 2.3 Monitor Resource Usage
```bash
# Check EC2 instance utilization
aws cloudwatch get-metric-statistics \
  --namespace AWS/EC2 \
  --metric-name CPUUtilization \
  --dimensions Name=InstanceId,Value=<INSTANCE_ID> \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average
```

### Step 3: Clean Up Resources

#### 3.1 Automated Cleanup Script
```bash
cat > /home/ubuntu/cleanup.sh << 'EOF'
#!/bin/bash
# Clean up old Docker images
docker image prune -a -f

# Clean up old log files
find /var/log -name "*.log" -mtime +30 -delete

# Clean up temporary files
find /tmp -type f -mtime +7 -delete
EOF

chmod +x /home/ubuntu/cleanup.sh

# Add to crontab for weekly cleanup
(crontab -l 2>/dev/null; echo "0 3 * * 0 /home/ubuntu/cleanup.sh") | crontab -
```

---

## Troubleshooting

### Common Issues and Solutions

#### Issue 1: EC2 Instance Not Accessible
**Symptoms:** Cannot SSH into EC2 instance

**Solutions:**
1. Check security group allows SSH (port 22)
2. Verify key pair is correct
3. Check instance state in EC2 console
4. Use AWS Systems Manager Session Manager as alternative

#### Issue 2: Docker Containers Not Starting
**Symptoms:** Docker containers fail to start

**Solutions:**
```bash
# Check Docker logs
docker-compose logs

# Check disk space
df -h

# Restart Docker service
sudo systemctl restart docker

# Check resource limits
free -h
```

#### Issue 3: Database Connection Issues
**Symptoms:** Application cannot connect to PostgreSQL

**Solutions:**
```bash
# Check PostgreSQL container status
docker ps | grep postgres

# Check PostgreSQL logs
docker logs finance-postgres

# Test database connection
docker exec finance-postgres psql -U finance_app -d financetracker -c "SELECT 1"
```

#### Issue 4: High CPU Usage
**Symptoms:** EC2 instance CPU usage > 80%

**Solutions:**
1. Check application logs for performance issues
2. Consider upgrading instance type
3. Implement caching
4. Optimize database queries

#### Issue 5: Out of Memory
**Symptoms:** Application crashes due to memory issues

**Solutions:**
```bash
# Check memory usage
free -h

# Check Docker memory usage
docker stats

# Add swap space
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
```

### Emergency Procedures

#### Rollback Deployment
```bash
# Rollback to previous version
cd /home/ubuntu/finance-tracker
git checkout <previous-commit-hash>
docker-compose down
docker-compose up -d
```

#### Restart All Services
```bash
# Emergency restart
cd /home/ubuntu/finance-tracker
docker-compose restart
```

#### Restore Database from Backup
```bash
# Download backup from S3
aws s3 cp s3://finance-tracker-backups/financetracker_YYYYMMDD_HHMMSS.sql /tmp/backup.sql

# Restore database
docker exec -i finance-postgres psql -U finance_app financetracker < /tmp/backup.sql
```

---

## Security Best Practices

### 1. Network Security
- Use security groups to restrict access
- Implement VPC with private subnets for databases
- Use NAT Gateway instead of public IP for private instances
- Enable VPC Flow Logs for monitoring

### 2. Application Security
- Keep all components updated
- Use environment variables for sensitive data
- Implement SSL/TLS for all communications
- Regular security audits

### 3. Data Security
- Encrypt data at rest (S3, EBS)
- Encrypt data in transit (HTTPS)
- Regular backups with encryption
- Implement access controls

### 4. IAM Security
- Use least privilege principle
- Rotate access keys regularly
- Enable MFA for root account
- Use IAM roles instead of access keys

---

## Conclusion

This guide provides a comprehensive approach to deploying the Finance Tracker application on AWS Free Tier. The setup includes:

- **Infrastructure:** VPC, EC2, ECR, S3, CloudWatch
- **Deployment:** Docker Compose on EC2
- **CI/CD:** GitHub Actions or AWS CodePipeline
- **Monitoring:** CloudWatch metrics and logs
- **Backup:** Automated database and file backups
- **Cost Management:** Budgets and optimization strategies

### Next Steps
1. Implement the infrastructure setup
2. Configure CI/CD pipeline
3. Set up monitoring and alerts
4. Test deployment workflow
5. Implement security best practices
6. Regular maintenance and updates

### Support Resources
- [AWS Documentation](https://docs.aws.amazon.com/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Docker Documentation](https://docs.docker.com/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)

---

## Appendix

### A. Complete Docker Compose for Production
See the production `docker-compose.yml` in the main documentation.

### B. Terraform Alternative
For infrastructure as code, consider using Terraform. Example:
```hcl
resource "aws_instance" "finance_tracker" {
  ami           = "ami-0c55b159cbfafe1f0"
  instance_type = "t2.micro"
  tags = {
    Name = "finance-tracker-server"
  }
}
```

### C. Quick Reference Commands
```bash
# SSH into EC2
ssh -i key.pem ubuntu@<PUBLIC_IP>

# Check Docker status
docker ps

# View logs
docker-compose logs -f

# Restart services
docker-compose restart

# Update application
git pull && docker-compose up -d --build
```
