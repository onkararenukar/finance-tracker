#!/bin/bash
# User data script for EC2 instance initialization
# This script sets up the EC2 instance with Docker, Docker Compose, and deploys the application

set -e

# Update system
echo "Updating system packages..."
sudo apt update && sudo apt upgrade -y

# Install Docker
echo "Installing Docker..."
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Add user to docker group
sudo usermod -aG docker ubuntu

# Install Docker Compose
echo "Installing Docker Compose..."
sudo curl -L "https://github.com/docker/compose/releases/download/${docker_compose_version}/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Install AWS CLI
echo "Installing AWS CLI..."
sudo apt install -y unzip
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
sudo unzip awscliv2.zip
sudo ./aws/install

# Install Git
echo "Installing Git..."
sudo apt install -y git

# Install Java 21 (required for local builds)
echo "Installing Java 21..."
sudo apt install -y openjdk-21-jdk

# Install Maven
echo "Installing Maven..."
sudo apt install -y maven

# Create application directory
echo "Creating application directory..."
mkdir -p /home/ubuntu/${project_name}
cd /home/ubuntu/${project_name}

# Clone repository (replace with your actual repository)
# git clone <YOUR_REPOSITORY_URL> .

# Create environment file
echo "Creating environment file..."
cat > .env << EOF
POSTGRES_PASSWORD=${postgres_password}
JWT_SECRET=${jwt_secret}
AWS_ACCESS_KEY_ID=${aws_access_key_id}
AWS_SECRET_ACCESS_KEY=${aws_secret_access_key}
AWS_REGION=us-east-1
S3_BUCKET_NAME=${s3_bucket_name}
ECR_REGISTRY=${ecr_repository}
ECR_REPOSITORY=${project_name}
EOF

# Create production docker-compose file
echo "Creating production docker-compose file..."
cat > docker-compose.prod.yml << 'EOF'
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
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U finance_app -d financetracker"]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: apache/kafka:3.8.0
    container_name: finance-kafka
    restart: unless-stopped
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    networks:
      - finance-net
    healthcheck:
      test: ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092"]
      interval: 10s
      timeout: 10s
      retries: 5

  api-gateway:
    image: ${ECR_REGISTRY}/${ECR_REPOSITORY}:api-gateway-latest
    container_name: finance-api-gateway
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      INGESTION_SERVICE_URL: http://ingestion-service:8081
      CATEGORIZATION_SERVICE_URL: http://categorization-service:8083
      ANALYTICS_SERVICE_URL: http://analytics-service:8084
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      DATABASE_URL: jdbc:postgresql://postgres:5432/financetracker
      DATABASE_USERNAME: finance_app
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      AWS_ACCESS_KEY_ID: ${AWS_ACCESS_KEY_ID}
      AWS_SECRET_ACCESS_KEY: ${AWS_SECRET_ACCESS_KEY}
      AWS_REGION: ${AWS_REGION}
      S3_BUCKET_NAME: ${S3_BUCKET_NAME}
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    networks:
      - finance-net
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  ingestion-service:
    image: ${ECR_REGISTRY}/${ECR_REPOSITORY}:ingestion-latest
    container_name: finance-ingestion-service
    restart: unless-stopped
    ports:
      - "8081:8081"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: financetracker
      DB_USER: finance_app
      DB_PASS: ${POSTGRES_PASSWORD}
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      UPLOAD_DIR: /data/uploads
      FINANCE_TRACKER_OCR_ENABLED: "true"
      FINANCE_TRACKER_OCR_LANGUAGE: "eng"
      FINANCE_TRACKER_OCR_DPI: "300"
      AWS_ACCESS_KEY_ID: ${AWS_ACCESS_KEY_ID}
      AWS_SECRET_ACCESS_KEY: ${AWS_SECRET_ACCESS_KEY}
      AWS_REGION: ${AWS_REGION}
      S3_BUCKET_NAME: ${S3_BUCKET_NAME}
    volumes:
      - uploads:/data/uploads
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    networks:
      - finance-net
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  parsing-service:
    image: ${ECR_REGISTRY}/${ECR_REPOSITORY}:parsing-latest
    container_name: finance-parsing-service
    restart: unless-stopped
    ports:
      - "8082:8082"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: financetracker
      DB_USER: finance_app
      DB_PASS: ${POSTGRES_PASSWORD}
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      LLM_CHAT_BASE_URL: ${LLM_CHAT_BASE_URL:-http://host.docker.internal:11434}
      LLM_CHAT_MODEL: ${LLM_CHAT_MODEL:-llama3.1}
      LLM_EMBEDDING_BASE_URL: ${LLM_EMBEDDING_BASE_URL:-http://host.docker.internal:11434}
      LLM_EMBEDDING_MODEL: ${LLM_EMBEDDING_MODEL:-nomic-embed-text}
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
      ingestion-service:
        condition: service_started
    networks:
      - finance-net
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  categorization-service:
    image: ${ECR_REGISTRY}/${ECR_REPOSITORY}:categorization-latest
    container_name: finance-categorization-service
    restart: unless-stopped
    ports:
      - "8083:8083"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: financetracker
      DB_USER: finance_app
      DB_PASS: ${POSTGRES_PASSWORD}
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      LLM_CHAT_BASE_URL: ${LLM_CHAT_BASE_URL:-http://host.docker.internal:11434}
      LLM_CHAT_MODEL: ${LLM_CHAT_MODEL:-llama3.1}
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
      parsing-service:
        condition: service_started
    networks:
      - finance-net
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8083/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  analytics-service:
    image: ${ECR_REGISTRY}/${ECR_REPOSITORY}:analytics-latest
    container_name: finance-analytics-service
    restart: unless-stopped
    ports:
      - "8084:8084"
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: financetracker
      DB_USER: finance_app
      DB_PASS: ${POSTGRES_PASSWORD}
    depends_on:
      postgres:
        condition: service_healthy
      parsing-service:
        condition: service_started
      categorization-service:
        condition: service_started
    networks:
      - finance-net
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8084/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

networks:
  finance-net:
    driver: bridge

volumes:
  postgres_data:
  uploads:
EOF

# Create backup script
echo "Creating backup script..."
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

# Create health check script
echo "Creating health check script..."
cat > /home/ubuntu/health-check.sh << 'EOF'
#!/bin/bash
# Check service health
SERVICES=("api-gateway" "ingestion-service" "parsing-service" "categorization-service" "analytics-service")

for service in "${SERVICES[@]}"; do
  if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "$service: HEALTHY"
  else
    echo "$service: UNHEALTHY"
  fi
done
EOF

chmod +x /home/ubuntu/health-check.sh

# Add to crontab for every 5 minutes
(crontab -l 2>/dev/null; echo "*/5 * * * * /home/ubuntu/health-check.sh") | crontab -

# Install CloudWatch agent
echo "Installing CloudWatch agent..."
wget https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb
sudo dpkg -i -E ./amazon-cloudwatch-agent.deb

# Configure CloudWatch agent
cat > /opt/aws/amazon-cloudwatch-agent/etc/config.json << 'EOF'
{
  "agent": {
    "metrics_collection_interval": 60,
    "run_as_user": "root"
  },
  "metrics": {
    "namespace": "FinanceTracker",
    "metrics_collected": {
      "cpu": {
        "measurement": ["cpu_usage_active", "cpu_usage_idle", "cpu_usage_system", "cpu_usage_user"],
        "metrics_collection_interval": 60
      },
      "disk": {
        "measurement": ["disk_used_percent"],
        "metrics_collection_interval": 60,
        "resources": ["*"]
      },
      "mem": {
        "measurement": ["mem_used_percent"],
        "metrics_collection_interval": 60
      }
    }
  },
  "logs": {
    "logs_collected": {
      "applications": {
        "docker": {
          "container_name": ".*",
          "file_path": "/var/lib/docker/containers/*/*.log",
          "log_group_name": "/finance-tracker/application",
          "log_stream_name": "{container_name}"
        }
      }
    }
  }
}
EOF

# Start CloudWatch agent
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -s

echo "Setup completed successfully!"
echo "Application is ready for deployment via CI/CD pipeline or manual deployment."
