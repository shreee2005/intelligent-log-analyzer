# Intelligent Log Analysis Platform

Event-driven log observability platform for log ingestion, search, metrics, anomaly detection, trace correlation, and alert delivery.

## Architecture

This project implements a microservices architecture using:
- **Spring Boot 4.0 / Java 21** - Application framework
- **Spring Cloud** - Microservices patterns
- **Apache Kafka** - Real-time log streaming
- **Kafka Streams** - Stream processing
- **Elasticsearch** - Log search and analytics
- **PostgreSQL** - Metadata storage
- **Redis** - Caching and real-time data
- **React + Vite** - Frontend dashboard

## Services

- **log-ingestion-service** - Log collection and normalization
- **stream-processor-service** - Real-time log processing
- **analysis-service** - Redis-backed volume analysis and statistical anomaly detection
- **search-service** - Log search and retrieval
- **alert-service** - Multi-channel alerting
- **api-gateway** - API gateway and routing
- **auth-service** - Users, projects, API keys, authorization, and custom metrics

## Project Documentation

Use the documentation set as the handoff source of truth:

- [Project handoff](docs/PROJECT_HANDOFF.md) - start here for current stage, next work, code navigation, and validation rules.
- [Architecture](docs/ARCHITECTURE.md) - services, data flow, Kafka flow, tenancy, tracing, and storage.
- [Deep system reference](docs/DEEP_SYSTEM_REFERENCE.md) - execution traces, data contracts, key design, and production caveats.
- [API and event reference](docs/API_REFERENCE.md) - HTTP endpoints, authentication, payloads, and anomaly contracts.
- [File-by-file reference](docs/FILE_REFERENCE.md) - purpose of the application source files and test coverage boundaries.
- [Interview and learning guide](docs/INTERVIEW_GUIDE.md) - design explanations, trade-offs, scaling calculations, and ML strategy.
- [Implementation status](docs/IMPLEMENTATION_STATUS.md) - what is implemented, verified, incomplete, or blocked.
- [Operations](docs/OPERATIONS.md) - local setup, build commands, smoke tests, and failure checks.
- [Roadmap](docs/ROADMAP.md) - remaining phases and definitions of done.
- [Development and handoff guide](docs/CONTRIBUTING.md) - how to continue work safely.

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