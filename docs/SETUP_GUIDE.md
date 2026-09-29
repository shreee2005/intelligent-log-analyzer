# Intelligent Log Analyzer - Setup Guide

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 21 | Spring Boot services |
| Maven | 3.9+ | Build tool |
| Node.js | 20+ | Frontend (Vite/React) |
| Docker | 24+ | Infrastructure containers |
| Docker Compose | 2.20+ | Multi-container orchestration |
| PostgreSQL | 15 | Primary database (auth-service) |
| Redis | 7 | Caching, metrics, sessions |
| Elasticsearch | 8.x/9.x | Log search & indexing |
| Apache Kafka | 7.5+ | Event streaming |
| Zookeeper | 3.8+ | Kafka coordination |

---

## Quick Start (Docker Compose)

### 1. Start Infrastructure
```bash
cd docker
docker-compose up -d
```

Verify all services healthy:
```bash
docker-compose ps
# All should show "Up" or "healthy"
```

### 2. Build All Services
```bash
cd ..
mvn clean install -DskipTests
```

### 3. Run Services (Terminal per service)
```bash
# Terminal 1: Auth Service
cd auth-service && mvn spring-boot:run

# Terminal 2: Log Ingestion
cd log-ingestion-service && mvn spring-boot:run

# Terminal 3: Search Service
cd search-service && mvn spring-boot:run

# Terminal 4: Analysis Service
cd analysis-service && mvn spring-boot:run

# Terminal 5: Stream Processor
cd stream-processor-service && mvn spring-boot:run

# Terminal 6: Alert Service
cd alert-service && mvn spring-boot:run

# Terminal 7: API Gateway
cd api-gateway && mvn spring-boot:run
```

### 4. Start Frontend
```bash
cd frontend-dashboard
npm install
npm run dev
# Opens at http://localhost:5173
```

---

## Configuration

### Environment Variables

Create `.env` in project root (or export in shell):

```bash
# Database
POSTGRES_DB=loganalyzer
POSTGRES_USER=admin
POSTGRES_PASSWORD=your_secure_db_password
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/loganalyzer
SPRING_DATASOURCE_USERNAME=admin
SPRING_DATASOURCE_PASSWORD=your_secure_db_password

# Redis
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379

# Kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Elasticsearch
SPRING_ELASTICSEARCH_URIS=http://localhost:9200

# Auth Service
JWT_SECRET=your-super-secret-jwt-key-min-32-chars-long-for-hs256
AUTH_SERVICE_URL=http://localhost:8091
FRONTEND_URL=http://localhost:5173

# OAuth2 (Optional - for GitHub/Google login)
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret

# Email (Optional - for alerts)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your-email@gmail.com
SPRING_MAIL_PASSWORD=your-app-password
```

### Service-Specific Configs

Each service has `application.yml` and `application-docker.yml`:

| Service | Config File | Key Properties |
|---------|-------------|----------------|
| auth-service | `auth-service/src/main/resources/application.yml` | JWT secret, DB, Redis, OAuth2 |
| log-ingestion | `log-ingestion-service/src/main/resources/application.yml` | Kafka, Auth service URL |
| search-service | `search-service/src/main/resources/application.yml` | Elasticsearch, Kafka, Auth URL |
| analysis-service | `analysis-service/src/main/resources/application.yml` | Redis, Kafka |
| stream-processor | `stream-processor-service/src/main/resources/application.yml` | Kafka Streams, Redis |
| alert-service | `alert-service/src/main/resources/application.yml` | Kafka, Mail, PostgreSQL |
| api-gateway | `api-gateway/src/main/resources/application.yml` | Route definitions |

---

## Database Setup

### Automatic (Flyway)
Auth-service runs Flyway migrations on startup:
- `V1__init_schema.sql` - Creates users, projects tables

### Manual (if needed)
```sql
-- Connect to PostgreSQL
psql -h localhost -U admin -d loganalyzer

-- Verify tables
\dt

-- Check users
SELECT * FROM users;

-- Check projects
SELECT * FROM projects;
```

---

## Elasticsearch Setup

### Index Template (Auto-created)
Search-service creates `logs` index on first document.

### Manual Index Creation (if needed)
```bash
curl -X PUT "localhost:9200/logs" -H 'Content-Type: application/json' -d'
{
  "mappings": {
    "properties": {
      "projectId": { "type": "long" },
      "serviceId": { "type": "keyword" },
      "level": { "type": "keyword" },
      "message": { "type": "text", "analyzer": "standard" },
      "timestamp": { "type": "date" },
      "traceId": { "type": "keyword" },
      "spanId": { "type": "keyword" },
      "parentSpanId": { "type": "keyword" }
    }
  }
}'
```

---

## Kafka Topics

Auto-created via `spring.kafka.bootstrap-servers` with `auto.create.topics.enable=true`.

### Manual Creation (if needed)
```bash
# logs.raw - 6 partitions for ingestion parallelism
kafka-topics.sh --create --topic logs.raw \
  --partitions 6 --replication-factor 1 \
  --bootstrap-server localhost:9092

# logs.anomalies - 3 partitions
kafka-topics.sh --create --topic logs.anomalies \
  --partitions 3 --replication-factor 1 \
  --bootstrap-server localhost:9092
```

---

## OAuth2 Setup (GitHub & Google)

### GitHub OAuth App
1. Go to GitHub Settings → Developer settings → OAuth Apps → New OAuth App
2. Application name: `Intelligent Log Analyzer`
3. Homepage URL: `http://localhost:8091`
4. Authorization callback URL: `http://localhost:8091/login/oauth2/code/github`
5. Copy Client ID and Client Secret to env vars

### Google OAuth Client
1. Go to Google Cloud Console → APIs & Services → Credentials
2. Create OAuth 2.0 Client ID
3. Authorized redirect URI: `http://localhost:8091/login/oauth2/code/google`
4. Copy Client ID and Client Secret to env vars

---

## Frontend Configuration

### `frontend-dashboard/src/config.js`
```javascript
export const API_BASE_URL = 'http://localhost:8080';
export const AUTH_BASE_URL = 'http://localhost:8091';
```

### Vite Proxy (Development)
```javascript
// vite.config.js
export default defineConfig({
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8091', changeOrigin: true },
    }
  }
})
```

---

## Verification Checklist

After startup, verify:

| Check | Command | Expected |
|-------|---------|----------|
| Auth Service | `curl http://localhost:8091/actuator/health` | `{"status":"UP"}` |
| Log Ingestion | `curl http://localhost:8086/actuator/health` | `{"status":"UP"}` |
| Search Service | `curl http://localhost:8084/actuator/health` | `{"status":"UP"}` |
| Analysis Service | `curl http://localhost:8083/actuator/health` | `{"status":"UP"}` |
| Stream Processor | `curl http://localhost:8082/actuator/health` | `{"status":"UP"}` |
| Alert Service | `curl http://localhost:8085/actuator/health` | `{"status":"UP"}` |
| API Gateway | `curl http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| Elasticsearch | `curl http://localhost:9200/_cluster/health` | `green` or `yellow` |
| Kafka | `kafka-topics.sh --list --bootstrap-server localhost:9092` | `logs.raw`, `logs.anomalies` |
| Redis | `redis-cli ping` | `PONG` |
| PostgreSQL | `psql -h localhost -U admin -d loganalyzer -c "SELECT 1"` | `1` |

---

## Test Log Ingestion

### Using cURL
```bash
# Get API key from dashboard after creating project
API_KEY="your-api-key-from-dashboard"

curl -X POST http://localhost:8080/api/v1/logs \
  -H "X-API-KEY: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "serviceId": "test-service",
    "level": "ERROR",
    "format": "JSON",
    "message": "Test error message",
    "timestamp": "2026-01-15T10:30:00Z"
  }'
```

### Using PowerShell (Windows)
```powershell
$apiKey = "your-api-key"
$body = @{
  serviceId = "test-service"
  level = "ERROR"
  format = "JSON"
  message = "Test error from PowerShell"
  timestamp = "2026-01-15T10:30:00Z"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/logs" `
  -Method Post `
  -Headers @{ "X-API-KEY" = $apiKey } `
  -ContentType "application/json" `
  -Body $body
```

### Verify in Dashboard
1. Open http://localhost:5173
2. Login → Select Project → Dashboard tab
3. Check Live Metrics and Smart Log Reader

---

## Common Issues

### "Invalid API Key"
- Verify key in localStorage matches project
- Check auth-service logs for validation errors
- Rotate key in Settings tab if needed

### "Project access denied"
- Ensure JWT token is valid (not expired)
- Verify user owns the project
- Check ProjectAccessClient call to auth-service

### Elasticsearch connection refused
- Ensure Elasticsearch is running: `docker ps | grep elasticsearch`
- Check `SPRING_ELASTICSEARCH_URIS` matches ES URL
- Verify ES memory: `-Xms512m -Xmx512m`

### Kafka connection failed
- Ensure Kafka & Zookeeper running
- Check `SPRING_KAFKA_BOOTSTRAP_SERVERS`
- Verify topics exist: `kafka-topics.sh --list --bootstrap-server localhost:9092`

### Redis connection failed
- Ensure Redis running: `docker ps | grep redis`
- Check `SPRING_DATA_REDIS_HOST` and `PORT`

### OAuth2 "redirect_uri_mismatch"
- Verify callback URLs in GitHub/Google console match exactly
- Must be `http://localhost:8091/login/oauth2/code/{github|google}`

### Frontend "Network Error"
- Check API Gateway running on 8080
- Verify Vite proxy config
- Check browser console for CORS errors

---

## Production Deployment

### Docker Compose Production
```bash
docker-compose -f docker-compose.prod.yml up -d
```

### Kubernetes (Planned)
See `docs/OPERATIONS.md` for K8s manifests and Helm charts.

### Environment-Specific Configs
```bash
# Development
SPRING_PROFILES_ACTIVE=dev

# Production
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=very-long-random-string-min-32-chars
POSTGRES_PASSWORD=very-secure-password
```

---

## Development Workflow

### Code Changes
```bash
# Backend: Hot reload via Spring Boot DevTools (included)
# Just save file, service reloads automatically

# Frontend: Vite HMR
npm run dev
```

### Running Tests
```bash
# All services
mvn test

# Specific service
mvn test -pl auth-service

# Frontend
cd frontend-dashboard && npm run test
```

### Building for Production
```bash
# Backend JARs
mvn clean package -DskipTests

# Frontend
cd frontend-dashboard && npm run build
# Output in dist/
```

---

## Logs & Debugging

### Service Logs
```bash
# Follow service logs
tail -f auth-service/logs/application.log

# Or via Docker
docker-compose logs -f auth-service
```

### Kafka Debugging
```bash
# Consume raw logs
kafka-console-consumer.sh --topic logs.raw \
  --from-beginning --bootstrap-server localhost:9092

# Consume anomalies
kafka-console-consumer.sh --topic logs.anomalies \
  --from-beginning --bootstrap-server localhost:9092
```

### Redis Debugging
```bash
# View metrics keys
redis-cli KEYS "metrics:*"

# View anomaly list
redis-cli LRANGE "anomalies:1" 0 -1
```

### Elasticsearch Debugging
```bash
# Search all logs
curl -X GET "localhost:9200/logs/_search?pretty" -H 'Content-Type: application/json' -d'
{ "query": { "match_all": {} }, "size": 5 }'
```

---

## Port Reference

| Service | Port | Protocol |
|---------|------|----------|
| API Gateway | 8080 | HTTP |
| Auth Service | 8091 | HTTP |
| Log Ingestion | 8086 | HTTP (WebFlux) |
| Search Service | 8084 | HTTP |
| Analysis Service | 8083 | HTTP |
| Stream Processor | 8082 | HTTP |
| Alert Service | 8085 | HTTP |
| PostgreSQL | 5432 | TCP |
| Redis | 6379 | TCP |
| Elasticsearch | 9200 | HTTP |
| Kafka | 9092 | TCP |
| Zookeeper | 2181 | TCP |
| Frontend (Dev) | 5173 | HTTP |

---

## Next Steps

1. Create your first project in dashboard
2. Copy API key from Integrations Hub
3. Configure your application (Logback/Winston) to send logs
4. Explore Live Metrics and Smart Log Reader
5. Set up Custom Metrics for business KPIs
6. Configure Webhooks/Email alerts for anomalies