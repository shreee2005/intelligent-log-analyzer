# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy parent pom first for dependency resolution
COPY ../pom.xml /build/pom.xml

# Copy this module's pom
COPY pom.xml /build/ml-suggestion-service/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl ml-suggestion-service -am

# Copy source code
COPY src /build/ml-suggestion-service/src

# Build only this module
RUN mvn clean package -pl ml-suggestion-service -am -DskipTests

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