# Terraform configuration for Finance Tracker AWS deployment
# This creates all necessary AWS infrastructure for the application

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Local variables
locals {
  project_name = "finance-tracker"
  common_tags = {
    Project     = local.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

# Data sources
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# VPC Module
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.1.2"

  name = "${local.project_name}-vpc"
  cidr = var.vpc_cidr

  azs              = ["${var.aws_region}a", "${var.aws_region}b"]
  private_subnets   = ["10.0.1.0/24", "10.0.2.0/24"]
  public_subnets    = ["10.0.101.0/24", "10.0.102.0/24"]
  database_subnets  = ["10.0.201.0/24", "10.0.202.0/24"]

  enable_nat_gateway     = var.enable_nat_gateway
  single_nat_gateway    = true
  enable_dns_hostnames  = true
  enable_dns_support    = true

  tags = local.common_tags
}

# Security Group for EC2
resource "aws_security_group" "finance_tracker" {
  name_prefix = "${local.project_name}-"
  description = "Security group for Finance Tracker application"
  vpc_id      = module.vpc.vpc_id

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-sg"
  })
}

# Security Group Rules
resource "aws_vpc_security_group_ingress_rule" "ssh" {
  security_group_id = aws_security_group.finance_tracker.id
  cidr_ipv4         = var.allowed_ssh_cidr
  from_port         = 22
  ip_protocol       = "tcp"
  to_port           = 22
}

resource "aws_vpc_security_group_ingress_rule" "http" {
  security_group_id = aws_security_group.finance_tracker.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  ip_protocol       = "tcp"
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  security_group_id = aws_security_group.finance_tracker.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  ip_protocol       = "tcp"
  to_port           = 443
}

resource "aws_vpc_security_group_ingress_rule" "app_ports" {
  security_group_id = aws_security_group.finance_tracker.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 8080
  ip_protocol       = "tcp"
  to_port           = 8084
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.finance_tracker.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
  from_port         = 0
  to_port           = 0
}

# EC2 Key Pair
resource "aws_key_pair" "finance_tracker" {
  key_name   = "${local.project_name}-key"
  public_key = var.ssh_public_key

  tags = local.common_tags
}

# EC2 Instance
resource "aws_instance" "finance_tracker" {
  ami           = data.aws_ami.ubuntu.id
  instance_type = var.instance_type
  key_name      = aws_key_pair.finance_tracker.key_name

  subnet_id                   = module.vpc.public_subnets[0]
  vpc_security_group_ids      = [aws_security_group.finance_tracker.id]
  associate_public_ip_address = true

  user_data = base64encode(templatefile("${path.module}/user-data.sh", {
    docker_compose_version = var.docker_compose_version
    project_name            = local.project_name
    ecr_repository          = aws_ecr_repository.finance_tracker.repository_url
    postgres_password       = var.postgres_password
    jwt_secret              = var.jwt_secret
    aws_access_key_id       = var.aws_access_key_id
    aws_secret_access_key   = var.aws_secret_access_key
    s3_bucket_name          = aws_s3_bucket.uploads.id
  }))

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size
    delete_on_termination = true
    encrypted             = true
  }

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-server"
  })

  volume_tags = local.common_tags
}

# Elastic IP
resource "aws_eip" "finance_tracker" {
  instance = aws_instance.finance_tracker.id
  domain   = "vpc"

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-eip"
  })

  depends_on = [module.vpc.internet_gateway_id]
}

# ECR Repository
resource "aws_ecr_repository" "finance_tracker" {
  name                 = local.project_name
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = local.common_tags
}

# ECR Lifecycle Policy
resource "aws_ecr_lifecycle_policy" "finance_tracker" {
  repository = aws_ecr_repository.finance_tracker.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last 10 images"
        selection = {
          tagStatus     = "any"
          countType     = "imageCountMoreThan"
          countNumber   = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# S3 Bucket for uploads
resource "aws_s3_bucket" "uploads" {
  bucket = "${local.project_name}-uploads-${random_id.bucket_suffix.hex}"

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-uploads"
  })
}

resource "aws_s3_bucket_ownership_controls" "uploads" {
  bucket = aws_s3_bucket.uploads.id
  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_acl" "uploads" {
  depends_on = [aws_s3_bucket_ownership_controls.uploads]
  bucket = aws_s3_bucket.uploads.id
  acl    = "private"
}

resource "aws_s3_bucket_versioning" "uploads" {
  bucket = aws_s3_bucket.uploads.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# S3 Bucket for backups
resource "aws_s3_bucket" "backups" {
  bucket = "${local.project_name}-backups-${random_id.bucket_suffix.hex}"

  tags = merge(local.common_tags, {
    Name = "${local.project_name}-backups"
  })
}

resource "aws_s3_bucket_lifecycle_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id

  rule {
    id     = "backup-retention"
    status = "Enabled"

    expiration {
      days = 90
    }

    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

# Random ID for unique bucket names
resource "random_id" "bucket_suffix" {
  byte_length = 4
}

# CloudWatch Log Group
resource "aws_cloudwatch_log_group" "finance_tracker" {
  name              = "/finance-tracker/application"
  retention_in_days = 30

  tags = local.common_tags
}

# IAM Role for EC2
resource "aws_iam_role" "ec2_role" {
  name = "${local.project_name}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = local.common_tags
}

# IAM Policy for EC2
resource "aws_iam_role_policy" "ec2_policy" {
  name = "${local.project_name}-ec2-policy"
  role = aws_iam_role.ec2_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams"
        ]
        Resource = [
          aws_cloudwatch_log_group.finance_tracker.arn,
          "${aws_cloudwatch_log_group.finance_tracker.arn}:*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability"
        ]
        Resource = aws_ecr_repository.finance_tracker.arn
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.uploads.arn,
          "${aws_s3_bucket.uploads.arn}/*",
          aws_s3_bucket.backups.arn,
          "${aws_s3_bucket.backups.arn}/*"
        ]
      }
    ]
  })
}

# IAM Instance Profile
resource "aws_iam_instance_profile" "finance_tracker" {
  name = "${local.project_name}-instance-profile"
  role = aws_iam_role.ec2_role.name
}

# Attach instance profile to EC2
resource "aws_iam_instance_profile_association" "finance_tracker" {
  instance_id = aws_instance.finance_tracker.id
  instance_profile = aws_iam_instance_profile.finance_tracker.name
}

# CloudWatch Dashboard
resource "aws_cloudwatch_dashboard" "finance_tracker" {
  dashboard_name = "${local.project_name}-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/EC2", "CPUUtilization", "InstanceId", aws_instance.finance_tracker.id]
          ]
          period = 300
          stat   = "Average"
          region = var.aws_region
          title  = "EC2 CPU Utilization"
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/EC2", "NetworkIn", "InstanceId", aws_instance.finance_tracker.id],
            [".", "NetworkOut", ".", "."]
          ]
          period = 300
          stat   = "Sum"
          region = var.aws_region
          title  = "EC2 Network I/O"
        }
      }
    ]
  })
}

# CloudWatch Alarm for CPU
resource "aws_cloudwatch_metric_alarm" "high_cpu" {
  alarm_name          = "${local.project_name}-high-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors EC2 CPU utilization"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    InstanceId = aws_instance.finance_tracker.id
  }
}

# SNS Topic for alerts
resource "aws_sns_topic" "alerts" {
  name = "${local.project_name}-alerts"

  tags = local.common_tags
}

# SNS Subscription for email alerts
resource "aws_sns_topic_subscription" "email_alerts" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# Budget
resource "aws_budgets_budget" "monthly_budget" {
  name              = "${local.project_name}-monthly-budget"
  budget_type       = "COST"
  limit_amount      = var.budget_limit
  limit_unit        = "USD"
  time_period_end   = "2087-06-15"
  time_period_start = "2024-01-01"
  time_unit         = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE_OF_BUDGET"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }
}

# Outputs
output "ec2_public_ip" {
  description = "Public IP of the EC2 instance"
  value       = aws_eip.finance_tracker.public_ip
}

output "ec2_public_dns" {
  description = "Public DNS of the EC2 instance"
  value       = aws_instance.finance_tracker.public_dns
}

output "ecr_repository_url" {
  description = "URL of the ECR repository"
  value       = aws_ecr_repository.finance_tracker.repository_url
}

output "s3_upload_bucket" {
  description = "Name of the S3 upload bucket"
  value       = aws_s3_bucket.uploads.id
}

output "s3_backup_bucket" {
  description = "Name of the S3 backup bucket"
  value       = aws_s3_bucket.backups.id
}

output "cloudwatch_dashboard_url" {
  description = "URL of the CloudWatch dashboard"
  value       = "https://${var.aws_region}.console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.finance_tracker.dashboard_name}"
}
