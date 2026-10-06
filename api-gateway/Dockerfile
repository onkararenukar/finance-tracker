# Dockerfile - API Gateway
# Build with repo root as context: docker build -f api-gateway/Dockerfile -t finance-tracker/api-gateway .

FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# Install Maven for building
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml ./pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
# Copy all the other module directories needed for the parent POM
COPY ingestion-service/pom.xml ./ingestion-service/pom.xml
COPY parsing-service/pom.xml ./parsing-service/pom.xml
COPY categorization-service/pom.xml ./categorization-service/pom.xml
COPY sms-sync-agent/pom.xml ./sms-sync-agent/pom.xml
COPY analytics-service/pom.xml ./analytics-service/pom.xml

RUN mvn -pl api-gateway -am -q dependency:go-offline

COPY api-gateway/src ./api-gateway/src
RUN mvn -pl api-gateway -am clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY --from=builder /build/api-gateway/target/api-gateway.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
