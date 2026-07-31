# Intelligent Log Analysis Platform

AI-powered log analysis platform for automatic anomaly detection, log correlation, and intelligent root cause analysis.

## Architecture

This project implements a microservices architecture using:
- **Spring Boot 3.2** - Application framework
- **Spring Cloud** - Microservices patterns
- **Apache Kafka** - Real-time log streaming
- **Kafka Streams** - Stream processing
- **Elasticsearch** - Log search and analytics
- **PostgreSQL** - Metadata storage
- **Redis** - Caching and real-time data
- **React + TypeScript** - Frontend dashboard

## Services

- **log-ingestion-service** - Log collection and normalization
- **stream-processor-service** - Real-time log processing
- **analysis-service** - ML-based anomaly detection
- **search-service** - Log search and retrieval
- **alert-service** - Multi-channel alerting
- **api-gateway** - API gateway and routing

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- Node.js 18+ (for frontend)

### Local Development

1. Start infrastructure services:
```bash
docker-compose up -d