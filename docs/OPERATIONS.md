# Local Operations and Verification

## Required tools

- Java 21
- Maven
- Node.js and npm
- Docker Desktop with Compose
- Git

## Start infrastructure

From the repository root:

```powershell
docker compose -f docker\docker-compose.yml up -d
```

This starts:

| Component | Port |
| --- | ---: |
| Zookeeper | 2181 |
| Kafka host listener | 9092 |
| Elasticsearch | 9200 |
| PostgreSQL | 5432 |
| Redis | 6379 |

The compose file is a local development stack. It is not a production topology: Kafka and Elasticsearch are single-node, Kafka is plaintext, and Elasticsearch security is disabled.

## Required environment variables

At minimum, configure:

```text
POSTGRES_USER
POSTGRES_PASSWORD
JWT_SECRET
WEBHOOK_ENCRYPTION_KEY
```

OAuth credentials are required only when OAuth login is enabled:

```text
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
GITHUB_CLIENT_ID
GITHUB_CLIENT_SECRET
```

`WEBHOOK_ENCRYPTION_KEY` must be a base64-encoded 32-byte key. Never commit these values.

## Build commands

Build one service:

```powershell
mvn -f auth-service\pom.xml test
mvn -f log-ingestion-service\pom.xml test
mvn -f analysis-service\pom.xml test
mvn -f api-gateway\pom.xml test
```

Compile services whose tests require external infrastructure:

```powershell
mvn -f search-service\pom.xml package -DskipTests
mvn -f alert-service\pom.xml package -DskipTests
mvn -f stream-processor-service\pom.xml package -DskipTests
```

Build the dashboard:

```powershell
Set-Location frontend-dashboard
npm install
npm run build
```

Use `npm run lint` when the frontend lint baseline is configured.

## Kafka checks

List topics:

```powershell
docker exec -it kafka kafka-topics --bootstrap-server localhost:29092 --list
```

Describe the raw log topic:

```powershell
docker exec -it kafka kafka-topics --bootstrap-server localhost:29092 --describe --topic logs.raw
```

Inspect consumer lag:

```powershell
docker exec -it kafka kafka-consumer-groups --bootstrap-server localhost:29092 --group search-service --describe
```

Consume a topic during debugging:

```powershell
docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:29092 --topic logs.raw --from-beginning --property print.key=true
```

## Health checks

The services expose actuator endpoints according to their configuration. Verify the process and infrastructure separately:

```powershell
Invoke-WebRequest http://localhost:9200
redis-cli -h localhost ping
```

For Kafka, use the topic and consumer commands above. A running process is not enough; a consumer group must be able to read and advance offsets.

## End-to-end smoke test

1. Start Docker infrastructure.
2. Start auth-service, ingestion, search, analysis, stream processor, alert-service, gateway, and dashboard.
3. Register or log in through the dashboard.
4. Create a project and copy the API key once.
5. Send a log through the gateway:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/logs" `
  -Method Post `
  -Headers @{ "X-API-KEY" = "<project-api-key>" } `
  -ContentType "application/json" `
  -Body '{"serviceId":"workflow-api","level":"ERROR","format":"JSON","message":"database unavailable","timestamp":"2026-09-16T10:00:00Z"}'
```

6. Confirm a record appears in `logs.raw`.
7. Confirm search-service indexes the record.
8. Confirm the dashboard can search it only for the owning project.
9. Send a valid `traceparent` header and verify trace search.
10. Configure a webhook with a bearer token and a test receiver.
11. Generate a high-severity anomaly and inspect webhook delivery status in PostgreSQL.

## Failure checks

Test these deliberately in local development:

- Stop Elasticsearch and observe search lag/failure.
- Stop Redis and observe metrics/anomaly behavior.
- Stop alert receiver and verify pending deliveries and backoff.
- Restart alert-service and verify pending deliveries remain.
- Publish duplicate anomaly events and verify one delivery per subscription/event ID.
- Use a different user's JWT and verify project access is denied.

## Data and secret handling

- Do not paste API keys, JWTs, OAuth secrets, database passwords, or webhook secrets into committed files.
- Do not log complete log payloads in production.
- Treat local volumes as disposable development data unless backups are intentional.
