# Outputs for Finance Tracker AWS deployment

output "ec2_instance_id" {
  description = "ID of the EC2 instance"
  value       = aws_instance.finance_tracker.id
}

output "ec2_public_ip" {
  description = "Public IP address of the EC2 instance"
  value       = aws_eip.finance_tracker.public_ip
}

output "ec2_public_dns" {
  description = "Public DNS name of the EC2 instance"
  value       = aws_instance.finance_tracker.public_dns
}

output "ec2_private_ip" {
  description = "Private IP address of the EC2 instance"
  value       = aws_instance.finance_tracker.private_ip
}

output "vpc_id" {
  description = "ID of the VPC"
  value       = module.vpc.vpc_id
}

output "public_subnet_ids" {
  description = "IDs of the public subnets"
  value       = module.vpc.public_subnets
}

output "private_subnet_ids" {
  description = "IDs of the private subnets"
  value       = module.vpc.private_subnets
}

output "security_group_id" {
  description = "ID of the security group"
  value       = aws_security_group.finance_tracker.id
}

output "ecr_repository_url" {
  description = "URL of the ECR repository"
  value       = aws_ecr_repository.finance_tracker.repository_url
}

output "ecr_repository_name" {
  description = "Name of the ECR repository"
  value       = aws_ecr_repository.finance_tracker.name
}

output "s3_upload_bucket_name" {
  description = "Name of the S3 upload bucket"
  value       = aws_s3_bucket.uploads.id
}

output "s3_upload_bucket_arn" {
  description = "ARN of the S3 upload bucket"
  value       = aws_s3_bucket.uploads.arn
}

output "s3_backup_bucket_name" {
  description = "Name of the S3 backup bucket"
  value       = aws_s3_bucket.backups.id
}

output "s3_backup_bucket_arn" {
  description = "ARN of the S3 backup bucket"
  value       = aws_s3_bucket.backups.arn
}

output "cloudwatch_log_group_name" {
  description = "Name of the CloudWatch log group"
  value       = aws_cloudwatch_log_group.finance_tracker.name
}

output "cloudwatch_log_group_arn" {
  description = "ARN of the CloudWatch log group"
  value       = aws_cloudwatch_log_group.finance_tracker.arn
}

output "sns_topic_arn" {
  description = "ARN of the SNS topic for alerts"
  value       = aws_sns_topic.alerts.arn
}

output "cloudwatch_dashboard_url" {
  description = "URL of the CloudWatch dashboard"
  value       = "https://${var.aws_region}.console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.finance_tracker.dashboard_name}"
}

output "iam_role_arn" {
  description = "ARN of the IAM role for EC2"
  value       = aws_iam_role.ec2_role.arn
}

output "ssh_connection_string" {
  description = "SSH connection string to connect to EC2"
  value       = "ssh -i ${aws_key_pair.finance_tracker.key_name}.pem ubuntu@${aws_eip.finance_tracker.public_ip}"
}

output "application_url" {
  description = "URL of the deployed application"
  value       = "http://${aws_eip.finance_tracker.public_ip}:8080"
}

output "api_gateway_url" {
  description = "URL of the API Gateway"
  value       = "http://${aws_eip.finance_tracker.public_ip}:8080"
}

output "swagger_ui_url" {
  description = "URL of the Swagger UI"
  value       = "http://${aws_eip.finance_tracker.public_ip}:8080/swagger-ui/index.html"
}
