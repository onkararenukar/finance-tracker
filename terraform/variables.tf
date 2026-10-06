# Variables for Finance Tracker AWS deployment

variable "aws_region" {
  description = "AWS region for deployment"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, production)"
  type        = string
  default     = "production"
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "enable_nat_gateway" {
  description = "Enable NAT Gateway for private subnets"
  type        = bool
  default     = false
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t2.micro"
}

variable "root_volume_size" {
  description = "Root volume size in GB"
  type        = number
  default     = 30
}

variable "allowed_ssh_cidr" {
  description = "CIDR block allowed for SSH access"
  type        = string
  default     = "0.0.0.0/0"
}

variable "ssh_public_key" {
  description = "SSH public key for EC2 access"
  type        = string
  sensitive   = true
}

variable "docker_compose_version" {
  description = "Docker Compose version to install"
  type        = string
  default     = "2.24.0"
}

variable "postgres_password" {
  description = "PostgreSQL database password"
  type        = string
  sensitive   = true
}

variable "jwt_secret" {
  description = "JWT secret key for authentication"
  type        = string
  sensitive   = true
}

variable "aws_access_key_id" {
  description = "AWS Access Key ID for application"
  type        = string
  sensitive   = true
}

variable "aws_secret_access_key" {
  description = "AWS Secret Access Key for application"
  type        = string
  sensitive   = true
}

variable "s3_bucket_name" {
  description = "S3 bucket name for file uploads"
  type        = string
  default     = ""
}

variable "alert_email" {
  description = "Email address for alerts and notifications"
  type        = string
}

variable "budget_limit" {
  description = "Monthly budget limit in USD"
  type        = number
  default     = 20
}
