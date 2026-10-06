#!/bin/bash

##############################################################################
# Finance Tracker - Complete Setup Script
# 
# This script sets up the entire finance-tracker project including:
# - Java 25 installation
# - Docker daemon startup
# - Ollama LLM service setup
# - Tesseract OCR for scanned PDFs
# - fswatch for real-time SMS sync
# - Environment configuration
# - Project dependencies installation
# - Docker compose startup
##############################################################################

set -e  # Exit on error

echo "🚀 Finance Tracker Setup Script"
echo "================================"
echo ""

# Color codes for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print colored output
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Check if running on macOS
if [[ "$OSTYPE" != "darwin"* ]]; then
    print_error "This script is designed for macOS. Please adapt for your platform."
    exit 1
fi

# 1. Check and install Java 25
echo "📦 Step 1: Checking Java 25 installation..."
if java -version 2>&1 | grep -q "25\."; then
    print_success "Java 25 is already installed"
else
    print_warning "Java 25 not found. Installing via Homebrew..."
    if ! command -v brew &> /dev/null; then
        print_error "Homebrew not found. Please install from https://brew.sh"
        exit 1
    fi
    brew install openjdk@25
    export PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH"
    print_success "Java 25 installed"
fi

# 2. Check Docker
echo ""
echo "🐳 Step 2: Checking Docker..."
if ! command -v docker &> /dev/null; then
    print_error "Docker not found. Please install Docker Desktop from https://www.docker.com/products/docker-desktop"
    exit 1
fi

# Start Docker daemon if not running
if ! docker info &> /dev/null; then
    print_warning "Docker daemon not running. Starting Docker Desktop..."
    open -a Docker
    print_warning "Waiting for Docker to start..."
    sleep 15
    if ! docker info &> /dev/null; then
        print_error "Docker failed to start. Please start Docker Desktop manually."
        exit 1
    fi
fi
print_success "Docker is running"

# 3. Check and install Ollama
echo ""
echo "🤖 Step 3: Setting up Ollama LLM service..."
if ! command -v ollama &> /dev/null; then
    print_warning "Ollama not found. Installing via Homebrew..."
    brew install ollama
    print_success "Ollama installed"
else
    print_success "Ollama is already installed"
fi

# Start Ollama service
if ! brew services list | grep ollama | grep -q "started"; then
    print_warning "Starting Ollama service..."
    brew services start ollama
    sleep 5
fi
print_success "Ollama service is running"

# Pull required models
echo ""
echo "📥 Step 4: Pulling LLM models (this may take a while)..."
if ! ollama list | grep -q "llama3.1"; then
    print_warning "Pulling llama3.1 model..."
    ollama pull llama3.1
    print_success "llama3.1 model downloaded"
else
    print_success "llama3.1 model already available"
fi

if ! ollama list | grep -q "nomic-embed-text"; then
    print_warning "Pulling nomic-embed-text model..."
    ollama pull nomic-embed-text
    print_success "nomic-embed-text model downloaded"
else
    print_success "nomic-embed-text model already available"
fi

# 4. Install fswatch for SMS sync agent (optional)
echo ""
echo "📱 Step 5: Setting up SMS sync agent dependencies..."
if ! command -v fswatch &> /dev/null; then
    print_warning "fswatch not found. Installing for real-time SMS sync..."
    brew install fswatch
    print_success "fswatch installed"
else
    print_success "fswatch is already installed"
fi

# 5. Install Tesseract OCR for scanned PDF support
echo ""
echo "🔍 Step 6: Setting up OCR dependencies for scanned PDFs..."
if ! command -v tesseract &> /dev/null; then
    print_warning "Tesseract not found. Installing for OCR support..."
    brew install tesseract
    print_success "Tesseract installed"
else
    print_success "Tesseract is already installed"
fi

# 6. Set environment variables
echo ""
echo "🔧 Step 7: Setting up environment variables..."
if [ ! -f .env ]; then
    cat > .env << EOF
# LLM Configuration (Ollama)
LLM_CHAT_BASE_URL=http://host.docker.internal:11434
LLM_CHAT_MODEL=llama3.1
LLM_EMBEDDING_BASE_URL=http://host.docker.internal:11434
LLM_EMBEDDING_MODEL=nomic-embed-text

# Dashboard Origin (for CORS)
DASHBOARD_ORIGIN=http://localhost:5173

# OCR Configuration
FINANCE_TRACKER_OCR_ENABLED=true
FINANCE_TRACKER_OCR_LANGUAGE=eng
FINANCE_TRACKER_OCR_DPI=300
EOF
    print_success "Environment file created (.env)"
else
    print_success "Environment file already exists (.env)"
fi

# 7. Build project with Maven
echo ""
echo "🔨 Step 8: Building project with Maven..."
export PATH="/opt/homebrew/opt/openjdk@25/bin:$PATH"
if ! mvn clean install -DskipTests; then
    print_error "Maven build failed. Please check the errors above."
    exit 1
fi
print_success "Project built successfully"

# 8. Start Docker Compose
echo ""
echo "🚀 Step 9: Starting Docker Compose services..."
if ! docker compose up -d --build; then
    print_error "Docker compose failed to start. Please check the errors above."
    exit 1
fi
print_success "Docker Compose services started"

# 9. Wait for services to be healthy
echo ""
echo "⏳ Step 10: Waiting for services to be healthy..."
echo "This may take 1-2 minutes for all services to start up..."
sleep 30

# Check service health
echo ""
echo "🏥 Step 11: Checking service health..."
services=("postgres" "kafka" "ingestion-service" "parsing-service" "categorization-service" "analytics-service" "api-gateway")
all_healthy=true

for service in "${services[@]}"; do
    if docker compose ps | grep -q "$service.*healthy\|$service.*Up"; then
        print_success "$service is running"
    else
        print_warning "$service may still be starting..."
        all_healthy=false
    fi
done

if [ "$all_healthy" = false ]; then
    print_warning "Some services may still be starting. Check with: docker compose ps"
fi

# 10. Verify SMS sync agent setup
echo ""
echo "📱 Step 12: Verifying SMS sync agent setup..."
if [ -f "sms-sync-agent/target/sms-sync-agent.jar" ]; then
    print_success "SMS sync agent is built"
else
    print_warning "SMS sync agent not built. Building now..."
    mvn -pl sms-sync-agent -am package
    print_success "SMS sync agent built"
fi

# Check if user has granted Full Disk Access
print_warning "Make sure to grant Full Disk Access to your terminal in System Settings → Privacy & Security → Full Disk Access"

# 11. Test OCR functionality
echo ""
echo "🔍 Step 13: Testing OCR functionality..."
if command -v tesseract &> /dev/null; then
    tesseract --version
    print_success "Tesseract OCR is ready for scanned PDF processing"
else
    print_warning "Tesseract not available - OCR will be disabled"
fi

# 12. Setup React Frontend
echo ""
echo "🎨 Step 14: Setting up React Frontend..."
if [ ! -d "finance-tracker-frontend" ]; then
    print_warning "React frontend not found. Creating..."
    npm create vite@latest finance-tracker-frontend -- --template react-ts
    cd finance-tracker-frontend
    npm install
    npm install react-router-dom axios @mui/material @emotion/react @emotion/styled
    cd ..
    print_success "React frontend created and dependencies installed"
else
    print_success "React frontend already exists"
    cd finance-tracker-frontend
    npm install
    cd ..
    print_success "React frontend dependencies updated"
fi

# Final instructions
echo ""
echo "🎉 Setup Complete!"
echo "=================="
echo ""
echo "Your Finance Tracker is now running locally!"
echo ""
echo "📍 Access Points:"
echo "  - API Gateway:       http://localhost:8080"
echo "  - Kafka UI:          http://localhost:8090"
echo "  - PostgreSQL:        localhost:5432"
echo "  - Ollama API:        http://localhost:11434"
echo ""
echo "📝 Quick Start Commands:"
echo "  # Upload a bank statement (supports PDF, CSV, and scanned PDFs)"
echo "  curl -X POST http://localhost:8080/api/v1/statements \\"
echo "    -F \"file=@/path/to/statement.pdf\" -F \"bankName=Your Bank\""
echo ""
echo "  # Check pending categories"
echo "  curl http://localhost:8080/api/v1/transaction-categories/pending"
echo ""
echo "  # View logs"
echo "  docker compose logs -f"
echo ""
echo "  # Stop all services"
echo "  docker compose down"
echo ""
echo "🔍 OCR Support:"
echo "  - Text-based PDFs: Automatically extracted using PDFBox"
echo "  - Scanned PDFs: Fallback to Tesseract OCR for image-based documents"
echo "  - CSV exports: Native CSV parsing support"
echo "  - OCR is enabled by default with English language support"
echo ""
echo "📱 Optional: SMS Sync Agent"
echo "  To enable automatic SMS processing from your Mac's Messages app:"
echo "  1. Grant Full Disk Access to your terminal in System Settings"
echo "  2. Run: java -jar sms-sync-agent/target/sms-sync-agent.jar"
echo "  3. The agent will capture bank transaction alerts automatically"
echo ""
echo "🎨 React Frontend:"
echo "  The React dashboard is available at finance-tracker-frontend/"
echo "  To start the frontend:"
echo "  cd finance-tracker-frontend"
echo "  npm run dev"
echo "  The dashboard will be available at http://localhost:5173"
echo ""
echo "For more information, see README.md"
echo ""