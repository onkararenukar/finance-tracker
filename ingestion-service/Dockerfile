# Dockerfile - Ingestion Service
#
# IMPORTANT: build this with the REPO ROOT as the build context (so both
# the parent pom.xml and this module are visible), e.g. from the
# finance-tracker/ directory:
#
#   docker build -f ingestion-service/Dockerfile -t finance-tracker/ingestion-service .
#
# Multi-stage build: the `builder` stage has the full JDK + Maven needed
# to compile, but none of that build tooling ends up in the final image -
# only the compiled jar gets copied into a slim JRE-only runtime stage.
# This keeps the shipped image small and reduces attack surface.

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# Install Maven for building
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy the parent POM first (defines shared dependency versions / the
# module list), then this module's POM, before copying source code.
# Layering it this way means Docker can cache the Maven dependency
# download step and skip re-downloading everything when only .java
# files change on a rebuild.
COPY pom.xml ./pom.xml
COPY ingestion-service/pom.xml ./ingestion-service/pom.xml
# Copy all the other module directories needed for the parent POM
COPY parsing-service/pom.xml ./parsing-service/pom.xml
COPY categorization-service/pom.xml ./categorization-service/pom.xml
COPY sms-sync-agent/pom.xml ./sms-sync-agent/pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
COPY analytics-service/pom.xml ./analytics-service/pom.xml

RUN mvn -pl ingestion-service -am -q dependency:go-offline

COPY ingestion-service/src ./ingestion-service/src

RUN mvn -pl ingestion-service -am clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Install Tesseract OCR for scanned PDF support
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-eng \
    tesseract-ocr-osd \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root user - standard container security hygiene, limits
# blast radius if the app process is ever compromised.
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY --from=builder /build/ingestion-service/target/ingestion-service.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8081

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
