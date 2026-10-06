# Dockerfile - Categorization Service
# Build with repo root as context: docker build -f categorization-service/Dockerfile -t finance-tracker/categorization-service .

FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# Install Maven for building
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml ./pom.xml
COPY categorization-service/pom.xml ./categorization-service/pom.xml
# Copy all the other module directories needed for the parent POM
COPY ingestion-service/pom.xml ./ingestion-service/pom.xml
COPY parsing-service/pom.xml ./parsing-service/pom.xml
COPY sms-sync-agent/pom.xml ./sms-sync-agent/pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
COPY analytics-service/pom.xml ./analytics-service/pom.xml

RUN mvn -pl categorization-service -am -q dependency:go-offline

COPY categorization-service/src ./categorization-service/src
RUN mvn -pl categorization-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY --from=builder /build/categorization-service/target/categorization-service.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8083

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
