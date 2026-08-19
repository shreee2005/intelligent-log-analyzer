FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -pl search-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/search-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
