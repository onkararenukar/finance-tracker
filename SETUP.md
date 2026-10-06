# Finance Tracker Setup Guide

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java 21** or higher
- **Maven 3.8** or higher
- **Docker** and **Docker Compose**
- **Git** (for cloning the repository)

## Quick Start

### 1. Clone the Repository

```bash
cd /Users/onkarrenukar/Downloads/finance-tracker
```

### 2. Start Infrastructure Services

```bash
docker compose up -d postgres kafka kafka-ui
```

This will start:
- PostgreSQL with pgvector extension on port 5432
- Kafka on ports 9092-9093
- Kafka UI on port 8090

### 3. Build and Start Application Services

```bash
# Build all services
mvn clean package -DskipTests

# Start all services
docker compose up -d
```

This will start:
- API Gateway on port 8080
- Ingestion Service on port 8081
- Parsing Service on port 8082
- Categorization Service on port 8083
- Analytics Service on port 8084

### 4. Wait for Services to Start

Give the services 30-60 seconds to fully start. You can check the logs:

```bash
docker compose logs -f
```

Press Ctrl+C to stop watching logs.

### 5. Verify Services are Running

```bash
docker compose ps
```

All services should show "Up" status.

## Access Points

### 🌐 API Documentation (Swagger UI)

- **API Gateway**: http://localhost:8080/swagger-ui/index.html
- **Ingestion Service**: http://localhost:8081/swagger-ui/index.html
- **Categorization Service**: http://localhost:8083/swagger-ui/index.html
- **Analytics Service**: http://localhost:8084/swagger-ui/index.html

### 🔐 Default Login Credentials

| Username | Password | Role | Description |
|----------|----------|------|-------------|
| `admin` | `admin123` | ADMIN | Full system access |
| `user` | `user123` | USER | Regular user access |

### 📤 Upload Bank Statements

Use the ingestion service endpoint to upload bank statements:

**Direct API Call:**
```bash
curl -X POST http://localhost:8081/api/v1/statements \
  -F "file=@your-statement.pdf" \
  -F "bankName=Your Bank Name" \
  -H "X-User-Id: 1"
```

**Via API Gateway:**
```bash
curl -X POST http://localhost:8080/api/v1/statements \
  -F "file=@your-statement.pdf" \
  -F "bankName=Your Bank Name" \
  -H "X-User-Id: 1"
```

### 📊 View Analytics

```bash
# Get transaction summary
curl http://localhost:8084/api/v1/analytics/summary

# Get transactions list
curl "http://localhost:8084/api/v1/analytics/transactions?limit=10"

# Get category breakdown
curl http://localhost:8084/api/v1/analytics/by-category
```

### 🔍 Kafka UI

Monitor Kafka topics and messages:
- **Kafka UI**: http://localhost:8090

## Stopping the Application

```bash
# Stop all services
docker compose down

# Stop services and remove volumes (deletes all data)
docker compose down -v
```

## Troubleshooting

### Services won't start

1. Check Docker is running: `docker ps`
2. Check port conflicts: Ensure ports 8080-8084, 5432, 9092-9093, 8090 are available
3. Check logs: `docker compose logs <service-name>`

### Database connection issues

1. Verify PostgreSQL is running: `docker compose ps postgres`
2. Check database logs: `docker compose logs postgres`
3. Restart PostgreSQL: `docker compose restart postgres`

### Kafka connection issues

1. Verify Kafka is running: `docker compose ps kafka`
2. Check Kafka logs: `docker compose logs kafka`
3. Check Kafka UI for topic status: http://localhost:8090

### Authentication issues

1. Verify JWT secret is configured in `api-gateway/src/main/resources/application.yml`
2. Check API Gateway logs: `docker compose logs api-gateway`
3. Ensure default users were created (check API Gateway startup logs)

## File Upload Support

The application supports:

- **PDF files**: Text-based and scanned PDFs (with OCR)
- **CSV files**: Standard bank statement CSV format
- **Bank formats**: Multiple bank statement formats

### Supported File Types

- `.pdf` - PDF bank statements
- `.csv` - CSV bank statements
- `.txt` - Text-based statements

## Development

### Running Tests

```bash
# Run all tests
mvn test

# Run tests with coverage report
mvn test jacoco:report

# Run tests for specific module
mvn test -pl ingestion-service
```

### Rebuilding Services

```bash
# Rebuild specific service
docker compose build <service-name>

# Rebuild all services
docker compose build

# Rebuild and restart
docker compose up -d --build
```

### Viewing Logs

```bash
# View all logs
docker compose logs

# View specific service logs
docker compose logs <service-name>

# Follow logs in real-time
docker compose logs -f <service-name>
```

## Database Schema

The application uses PostgreSQL with the following tables:

- `users` - User accounts and authentication
- `bank_statements` - Uploaded bank statement metadata
- `transactions` - Parsed transaction data
- `transaction_embeddings` - Vector embeddings for similarity search
- `transaction_categories` - Transaction categorization results
- `categories` - Predefined transaction categories

## Architecture

The application follows a microservices architecture:

1. **API Gateway** (8080) - Authentication, routing, user management
2. **Ingestion Service** (8081) - File upload and text extraction
3. **Parsing Service** (8082) - Transaction parsing and embedding generation
4. **Categorization Service** (8083) - AI-powered transaction categorization
5. **Analytics Service** (8084) - Reporting and data visualization

## Additional Configuration

### Environment Variables

You can override default settings using environment variables:

```bash
# Database configuration
export DATABASE_URL=jdbc:postgresql://localhost:5432/financetracker
export DATABASE_USERNAME=finance_app
export DATABASE_PASSWORD=finance_password

# JWT configuration
export JWT_SECRET=your-256-bit-secret-key-change-this-in-production

# Service URLs
export INGESTION_SERVICE_URL=http://localhost:8081
export CATEGORIZATION_SERVICE_URL=http://localhost:8083
export ANALYTICS_SERVICE_URL=http://localhost:8084
```

### Configuring OCR

OCR is enabled by default. To configure:

```yaml
# In ingestion-service/src/main/resources/application.yml
finance-tracker:
  ocr:
    enabled: true
    language: eng
    dpi: 300
```

### Configuring LLM

The application can use Ollama for AI-powered features:

```yaml
# In relevant service application.yml
finance-tracker:
  llm:
    enabled: true
    base-url: http://localhost:11434
    chat-model: llama3.1
    embedding-model: nomic-embed-text
```

## Support

For issues or questions:

1. Check service logs: `docker compose logs <service-name>`
2. Verify service health: `curl http://localhost:<port>/actuator/health`
3. View Kafka topics: http://localhost:8090
4. Check API documentation: http://localhost:8080/swagger-ui/index.html

## Next Steps

After setup:

1. **Upload a test statement** using the ingestion endpoint
2. **Monitor processing** via Kafka UI
3. **View parsed transactions** via analytics endpoints
4. **Review categorizations** via categorization endpoints
5. **Explore the API** using Swagger UI

For detailed API documentation, see `API_DOCUMENTATION.md`.
