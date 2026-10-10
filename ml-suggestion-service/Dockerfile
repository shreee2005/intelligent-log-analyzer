# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy parent pom for dependency management
COPY pom.xml /build/pom.xml

# Copy only this module's pom
COPY ml-suggestion-service/pom.xml /build/ml-suggestion-service/pom.xml

# Download dependencies (cached layer) - build from module's pom directly
RUN mvn dependency:go-offline -f ml-suggestion-service/pom.xml

# Copy source code
COPY ml-suggestion-service/src /build/ml-suggestion-service/src

# Build only this module
RUN mvn clean package -f ml-suggestion-service/pom.xml -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy built jar
COPY --from=builder /build/ml-suggestion-service/target/*.jar app.jar

# Create non-root user
RUN addgroup -g 1000 -S appgroup && \
    adduser -u 1000 -S appuser -G appgroup && \
    chown appuser:appgroup app.jar

USER appuser

EXPOSE 8087

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]