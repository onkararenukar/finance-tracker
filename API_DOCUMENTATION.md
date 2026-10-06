# Finance Tracker API Documentation

## Overview

The Finance Tracker application provides a RESTful API for bank statement ingestion, transaction parsing, categorization, and analytics. The application is deployed as microservices with an API gateway for routing.

## Base URLs

- **API Gateway**: `http://localhost:8080`
- **Ingestion Service**: `http://localhost:8081`
- **Parsing Service**: `http://localhost:8082` (Kafka consumer only, no public REST API)
- **Categorization Service**: `http://localhost:8083`
- **Analytics Service**: `http://localhost:8084`
- **Kafka UI**: `http://localhost:8090`

## Authentication

The API Gateway uses JWT-based authentication. Include the JWT token in the `Authorization` header:

```
Authorization: Bearer <your-jwt-token>
```

### Authentication Endpoints

#### Register User
- **POST** `/api/v1/auth/register`
- **Description**: Register a new user account
- **Request Body**:
```json
{
  "username": "john.doe",
  "email": "john@example.com",
  "password": "securePassword123",
  "role": "USER"
}
```
- **Response**: User created with ID and default role

#### Login
- **POST** `/api/v1/auth/login`
- **Description**: Authenticate user and receive JWT token
- **Request Body**:
```json
{
  "username": "john.doe",
  "password": "securePassword123"
}
```
- **Response**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 1,
  "username": "john.doe",
  "role": "USER"
}
```

## API Endpoints

### Ingestion Service (via API Gateway)

#### Upload Bank Statement
- **POST** `/api/v1/statements`
- **Description**: Upload a bank statement (PDF or CSV) for processing
- **Headers**: 
  - `X-User-Id`: User ID for multi-tenant support (optional, defaults to admin)
- **Parameters**:
  - `bankName` (query): Name of the bank (e.g., "HDFC Bank")
  - `file` (form-data): The statement file (PDF or CSV)
- **Response**:
```json
{
  "id": 1,
  "bankName": "HDFC Bank",
  "originalFilename": "statement.pdf",
  "status": "PUBLISHED",
  "chunkCount": 3,
  "createdAt": "2026-09-15T08:30:00Z"
}
```
- **Status Codes**:
  - `201`: Statement uploaded successfully
  - `400`: Invalid file or parameters
  - `500`: Internal server error

### Categorization Service (via API Gateway)

#### List All Categories
- **GET** `/api/v1/categories`
- **Description**: Get all transaction categories
- **Response**:
```json
[
  {
    "id": 1,
    "name": "Groceries",
    "isSeeded": true
  },
  {
    "id": 2,
    "name": "Food & Dining",
    "isSeeded": true
  }
]
```

#### List Pending Categorizations
- **GET** `/api/v1/transaction-categories/pending`
- **Description**: Get transactions awaiting category review
- **Parameters**:
  - `page` (query, optional): Page number (default: 0)
  - `size` (query, optional): Page size (default: 20)
- **Response**:
```json
{
  "transactions": [
    {
      "transactionId": 101,
      "suggestedCategoryName": "Pet Care",
      "oneLineDescription": "Vet visit for the dog",
      "llmModel": "llama3.1",
      "createdAt": "2026-09-15T08:35:00Z"
    }
  ],
  "totalPages": 1,
  "totalElements": 1
}
```

#### Assign Category
- **POST** `/api/v1/transaction-categories/{transactionId}/assign`
- **Description**: Resolve a pending categorization
- **Path Parameters**:
  - `transactionId`: The transaction ID
- **Request Body**:
```json
{
  "categoryId": 5,
  "newCategoryName": null
}
```
OR
```json
{
  "categoryId": null,
  "newCategoryName": "Pet Care"
}
```
- **Status Codes**:
  - `204`: Category assigned successfully
  - `400`: Invalid request
  - `404`: Transaction not found

### Analytics Service (via API Gateway)

#### Get Transactions
- **GET** `/api/v1/analytics/transactions`
- **Description**: Get paginated transaction list with category and status
- **Parameters**:
  - `startDate` (query, optional): Start date (ISO format, default: 30 days ago)
  - `endDate` (query, optional): End date (ISO format, default: today)
  - `categoryId` (query, optional): Filter by category ID
  - `limit` (query, optional): Max results (1-200, default: 50)
  - `offset` (query, optional): Results to skip (default: 0)
- **Response**:
```json
{
  "transactions": [
    {
      "id": 1,
      "transactionDate": "2026-09-01",
      "description": "Coffee Shop",
      "amount": -4.50,
      "direction": "DEBIT",
      "categoryName": "Food & Dining",
      "categoryStatus": "CATEGORIZED"
    }
  ],
  "total": 1
}
```

#### Get Timeseries Data
- **GET** `/api/v1/analytics/timeseries`
- **Description**: Get income/expense totals bucketed by time period
- **Parameters**:
  - `startDate` (query, optional): Start date (ISO format)
  - `endDate` (query, optional): End date (ISO format)
  - `bucket` (query, optional): Time bucket - "day", "week", or "month" (default: "day")
- **Response**:
```json
{
  "data": [
    {
      "date": "2026-09-01",
      "income": 3000.00,
      "expense": 150.50
    },
    {
      "date": "2026-09-02",
      "income": 0.00,
      "expense": 75.25
    }
  ]
}
```

#### Get Category Breakdown
- **GET** `/api/v1/analytics/categories`
- **Description**: Get spending breakdown by category
- **Parameters**:
  - `startDate` (query, optional): Start date (ISO format)
  - `endDate` (query, optional): End date (ISO format)
- **Response**:
```json
{
  "categories": [
    {
      "categoryName": "Food & Dining",
      "totalAmount": 150.50,
      "transactionCount": 12
    },
    {
      "categoryName": "Transport",
      "totalAmount": 75.25,
      "transactionCount": 5
    }
  ]
}
```

### Admin Endpoints (via API Gateway)

#### Get All Users
- **GET** `/api/v1/admin/users`
- **Description**: Get all users (admin only)
- **Response**: Array of user objects

#### Create User
- **POST** `/api/v1/admin/users`
- **Description**: Create a new user (admin only)
- **Request Body**:
```json
{
  "username": "newuser",
  "email": "newuser@example.com",
  "password": "password123",
  "role": "USER"
}
```

#### Update User Role
- **PUT** `/api/v1/admin/users/{userId}/role`
- **Description**: Update user role (admin only)
- **Path Parameters**:
  - `userId`: The user ID
- **Request Body**:
```json
{
  "role": "ADMIN"
}
```

## Swagger UI

Interactive API documentation is available via Swagger UI:

- **API Gateway**: `http://localhost:8080/swagger-ui/index.html`
- **Ingestion Service**: `http://localhost:8081/swagger-ui/index.html`
- **Categorization Service**: `http://localhost:8083/swagger-ui/index.html`
- **Analytics Service**: `http://localhost:8084/swagger-ui/index.html`

## OpenAPI Specifications

OpenAPI JSON specifications are available at:

- **API Gateway**: `http://localhost:8080/v3/api-docs`
- **Ingestion Service**: `http://localhost:8081/v3/api-docs`
- **Categorization Service**: `http://localhost:8083/v3/api-docs`
- **Analytics Service**: `http://localhost:8084/v3/api-docs`

## Error Responses

All endpoints may return standard error responses:

```json
{
  "timestamp": "2026-09-15T08:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid request parameters",
  "path": "/api/v1/statements"
}
```

Common status codes:
- `400`: Bad Request - Invalid parameters
- `401`: Unauthorized - Missing or invalid token
- `403`: Forbidden - Insufficient permissions
- `404`: Not Found - Resource not found
- `500`: Internal Server Error - Server error

## Rate Limiting

Rate limiting is applied at the API Gateway level:
- Standard users: 100 requests per minute
- Admin users: 500 requests per minute

Rate limit headers are included in responses:
- `X-RateLimit-Limit`: Request limit
- `X-RateLimit-Remaining`: Remaining requests
- `X-RateLimit-Reset`: Reset time (Unix timestamp)

## WebSockets

The API Gateway provides a WebSocket endpoint for live event streaming:

- **Endpoint**: `ws://localhost:8080/ws/live-events`
- **Description**: Real-time Kafka event streaming for dashboard visualization
- **Authentication**: JWT token required as query parameter: `?token=<your-jwt-token>`

## Data Models

### Transaction
```json
{
  "id": 1,
  "statementId": 42,
  "bankName": "HDFC Bank",
  "transactionDate": "2026-09-01",
  "description": "Coffee Shop",
  "amount": -4.50,
  "direction": "DEBIT",
  "status": "PUBLISHED",
  "userId": 1,
  "createdAt": "2026-09-15T08:30:00Z"
}
```

### Category
```json
{
  "id": 1,
  "name": "Food & Dining",
  "isSeeded": true
}
```

### TransactionCategory
```json
{
  "transactionId": 101,
  "categoryId": 1,
  "status": "CATEGORIZED",
  "suggestedCategoryName": "Food & Dining",
  "oneLineDescription": "Lunch at a cafe",
  "llmModel": "llama3.1",
  "version": 0,
  "createdAt": "2026-09-15T08:35:00Z",
  "updatedAt": "2026-09-15T08:35:00Z"
}
```

## Kafka Topics

The application uses Kafka for event-driven communication:

- `statement.ingested`: Published when a statement is uploaded and text extracted
- `transaction.parsed`: Published when transactions are parsed from statements
- `statement.ingested.DLT`: Dead-letter topic for failed statement ingestion
- `transaction.parsed.DLT`: Dead-letter topic for failed transaction parsing

## Multi-Tenancy

The application supports multi-tenancy through user-based data isolation:

- Each user's data is isolated by `user_id` in database tables
- Users can only access their own statements and transactions
- Admin users have access to all data for management purposes
- Include `X-User-Id` header in requests to specify the user context

## External Dependencies

The application requires the following external services:

1. **PostgreSQL with pgvector**: For data persistence and vector embeddings
2. **Kafka**: For event-driven communication between services
3. **Ollama** (optional): For LLM-based transaction parsing and categorization
   - Default endpoint: `http://localhost:11434`
   - Default chat model: `llama3.1`
   - Default embedding model: `nomic-embed-text`

## Support

For issues or questions:
- Check service logs: `docker compose logs <service-name>`
- Verify service health: `http://localhost:<port>/actuator/health`
- View Kafka topics: `http://localhost:8090` (Kafka UI)
