# Intelligent Log Analyzer - Architecture Documentation

## System Overview

```
┌─────────────────────────────┐
│  External Log Producer      │
└──────────────┬──────────────┘
               │ (X-API-KEY / traceparent)
               ▼
      ┌─────────────────────┐
      │  API Gateway :8080  │
      └──────────┬──────────┘
                 │
                 ▼
┌─────────────────────────────┐
│ log-ingestion-service :8086 │
└──────────────┬──────────────┘
               │ (Validate key via auth-service :8091)
               │ (Redact PII, extract W3C trace)
               ▼
       Kafka Topic: `logs.raw`
┌───────────────┼───────────────┼───────────────┐
▼               ▼               ▼
search-      analysis-      stream-
service      service       processor
:8084        :8083         :8082
    │            │              │
    ▼            ▼              ▼
Elasticsearch  Redis         Kafka Streams
(`logs` idx)  (1-min buckets)  (regex metrics &
                                        window anomalies)
                                               │
                                               ▼
                                     Kafka Topic: `logs.anomalies`
                                               │
                                               ▼
                                    ┌──────────────────────────┐
                                    │   alert-service :8085    │
                                    └────────────┬─────────────┘
                                                 │
                                    ┌────────────┴────────────┐
                                    ▼                         ▼
                              Java Mail                 PostgreSQL Outbox
                            (Email Alerts)           (Signed Webhook Retries)
```

---

## Microservices

| Service | Port | Purpose | Key Technologies |
|---------|------|---------|------------------|
| **api-gateway** | 8080 | Reverse proxy, routing, CORS | Spring Cloud Gateway |
| **auth-service** | 8091 | Auth, JWT, Projects, API Keys, OAuth2 | Spring Security, JPA, Redis, Flyway |
| **log-ingestion-service** | 8086 | Log ingestion, PII redaction, Trace context | WebFlux, Kafka Producer |
| **search-service** | 8084 | Elasticsearch indexing, log search | Elasticsearch, Kafka Consumer |
| **analysis-service** | 8083 | Metrics aggregation, Z-Score anomaly detection | Redis, Kafka Consumer, Scheduled tasks |
| **stream-processor-service** | 8082 | Kafka Streams, custom metrics, window anomalies | Kafka Streams |
| **alert-service** | 8085 | Alert consumption, email, webhook delivery | Kafka Consumer, Java Mail, PostgreSQL |

---

## Data Flow

### 1. Log Ingestion Flow
```
Client (Logback/Winston/PowerShell)
    │
    ├── POST /api/v1/logs
    │   ├── Headers: X-API-KEY, traceparent (optional)
    │   └── Body: { serviceId, level, message, timestamp, format }
    │
    ▼
API Gateway → log-ingestion-service
    │
    ├── Validate X-API-KEY via auth-service POST /api/projects/key/validate
    │   └── Returns projectId (overwrites any body projectId)
    │
    ├── Extract W3C traceparent → traceId, spanId, parentSpanId
    │
    ├── Redact PII (emails, JWTs, API keys, passwords, IPs)
    │
    ├── Enrich with projectId, trace context
    │
    └── Push to Kafka: logs.raw
```

### 2. Search Indexing Flow
```
Kafka: logs.raw
    │
    ▼
search-service (SearchKafkaConsumer)
    │
    ├── Parse LogRaw → LogDocument
    ├── Index into Elasticsearch `logs` index
    │   └── Fields: projectId, serviceId, level, message, timestamp, traceId, spanId
    │
    └── Acknowledges offset
```

### 3. Metrics & Anomaly Detection Flow
```
Kafka: logs.raw
    │
    ▼
analysis-service (AnalyticsKafkaConsumer)
    │
    ├── Increment Redis counters: metrics:{projectId}:{serviceId}:{minute}
    │   └── Hash fields: TOTAL, ERROR, WARN
    │
    └── Track service bucket indexes for querying
    │
    ▼ (Every minute via @Scheduled)
AnomalyDetectionService
    │
    ├── Get 24h historical volumes per service
    ├── Calculate mean, stdDev
    ├── Get current minute volume
    ├── Z-Score = (current - mean) / stdDev
    │
    ├── If |Z| > 3 → ANOMALY_DETECTED
    │   ├── Type: SPIKE (Z > 3) or DROP (Z < -3)
    │   ├── Severity: HIGH (|Z| 3-5) or CRITICAL (|Z| > 5)
    │   ├── Deduplication key: projectId:serviceId:volume:minute
    │   └── Push to Kafka: logs.anomalies
    │
    └── Save anomaly to Redis list (last 50 per project)
```

### 4. Alerting Flow
```
Kafka: logs.anomalies
    │
    ▼
alert-service (AlertKafkaConsumer)
    │
    ├── Parse VolumeAnomaly / AnomalyEvent
    ├── Filter: only HIGH/CRITICAL severity
    │
    ├── Email Alert (NotificationService)
    │   └── JavaMailSender → SMTP
    │
    └── Webhook Alert (WebhookDeliveryScheduler)
        ├── AES-GCM encrypt payload
        ├── HMAC-SHA256 sign
        ├── Store in PostgreSQL outbox
        ├── Retry with exponential backoff
        └── Move to DLQ after 5 failures
```

---

## Authentication & Authorization

### Two Authentication Types

| Type | Header | Used By | Validation |
|------|--------|---------|------------|
| **API Key** | `X-API-KEY` | External log producers | SHA-256 hash lookup in auth-service |
| **JWT** | `Authorization: Bearer <token>` | Human users (dashboard) | HS256 signature, 24h expiry |

### API Key Flow
```
1. User creates project via dashboard (JWT auth)
2. Backend generates UUID v4 → rawApiKey
3. Hash: SHA-256(rawApiKey) → apiKeyHash (stored in DB)
4. Return rawApiKey ONCE to user (stored in localStorage)
5. Client sends X-API-KEY: rawApiKey with logs
6. log-ingestion-service → auth-service validate
7. auth-service hashes incoming key, looks up apiKeyHash
8. Returns projectId if valid and active
```

### JWT Flow
```
1. User logs in (email/password or OAuth2)
2. auth-service generates HS256 JWT (24h expiry)
3. Contains: email (subject), roles, project access
4. Frontend stores in localStorage
5. All dashboard API calls include Bearer token
6. Services validate via JwtAuthenticationFilter
7. Tenant isolation via ProjectAccessClient → auth-service GET /api/projects/{id}/access
```

### OAuth2 Flow (GitHub/Google)
```
1. User clicks "Continue with GitHub"
2. Redirect to GitHub OAuth consent
3. GitHub redirects to /login/oauth2/code/github
4. Spring Security exchanges code for access token
5. Fetches user info from GitHub /user endpoint
6. OAuth2SuccessHandler:
   a. Try get email from user info
   b. If null (private email) → call GitHub /user/emails with access token
   c. Pick primary+verified email
   d. Find or create user by email (password_hash = 'OAUTH_USER_NO_PASSWORD')
   e. Generate JWT
7. Redirect to frontend: /#token=JWT&email=user@domain.com
8. Frontend parses hash, saves to localStorage
```

---

## Tenant Isolation (projectId)

All multi-tenant operations require `projectId`:

| Endpoint | Isolation Check |
|----------|-----------------|
| GET /api/v1/search?projectId=X | ProjectAccessClient.hasAccess(projectId, JWT) |
| GET /api/v1/analytics/metrics/{svc}?projectId=X | Same |
| GET /api/projects/{id}/access | Direct ownership check |
| POST /api/v1/logs (API Key) | API key validates to projectId |

**ProjectAccessClient** makes lightweight HTTP call to auth-service with user's JWT.

---

## Key Data Models

### User (auth-service)
```java
@Entity @Table("users")
- id: Long
- email: String (unique)
- passwordHash: String (BCrypt, or 'OAUTH_USER_NO_PASSWORD')
- roles: Set<Role> (ROLE_OWNER)
```

### Project (auth-service)
```java
@Entity @Table("projects")
- id: Long
- name: String
- owner: User (ManyToOne)
- apiKeyHash: String (SHA-256, unique)
- apiKeyActive: Boolean
- @Transient apiKey: String (raw key, NOT persisted)
```

### LogDocument (search-service)
```java
@Document(indexName="logs")
- id: String (UUID)
- projectId: Long
- serviceId: String (Keyword)
- level: String (Keyword)
- message: Text (standard analyzer)
- timestamp: Instant (Date)
- traceId: String (Keyword)
- spanId: String (Keyword)
- parentSpanId: String (Keyword)
```

### VolumeAnomaly (analysis-service)
```java
- schemaVersion: int
- eventType: "ANOMALY_DETECTED"
- eventId: UUID
- projectId: Long
- serviceId: String
- detector: "VOLUME_Z_SCORE"
- severity: "HIGH" | "CRITICAL"
- deduplicationKey: String
- timestamp: Instant
- zScore: double
- currentVolume: long
- meanVolume: double
- stdDev: double
- type: "SPIKE" | "DROP"
```

---

## Kafka Topics

| Topic | Partitions | Retention | Producers | Consumers |
|-------|------------|-----------|-----------|-----------|
| `logs.raw` | 6 | 7 days | log-ingestion-service | search-service, analysis-service, stream-processor |
| `logs.anomalies` | 3 | 7 days | analysis-service | alert-service |
| `logs.metrics` | 3 | 1 day | stream-processor | (internal) |

---

## Redis Keys

| Pattern | TTL | Purpose |
|---------|-----|---------|
| `metrics:{projectId}:{serviceId}:{minute}` | 24h | Minute-level counters (TOTAL, ERROR, WARN) |
| `service_buckets:{projectId}:{serviceId}` | 25h | Set of active minute buckets |
| `service_bucket_indexes` | 25h | Set of all service bucket keys |
| `anomalies:{projectId}` | 7d | List of last 50 anomalies (JSON) |
| `anomaly_dedup:{key}` | 24h | Deduplication for anomaly alerts |

---

## Database Schema (PostgreSQL - auth-service)

```sql
-- Flyway migration V1__init_schema.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(50) NOT NULL
);

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    api_key_hash VARCHAR(64) UNIQUE,  -- SHA-256 hex
    api_key_active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_projects_owner ON projects(owner_id);
CREATE INDEX idx_projects_api_key_hash ON projects(api_key_hash);
```

---

## Security Considerations

1. **API Keys hashed with SHA-256** - Never stored in plaintext
2. **PII Redaction** - Applied at ingestion (emails, JWTs, keys, passwords, IPs)
3. **W3C Trace Context** - Propagated via traceparent header
4. **Webhook Signing** - HMAC-SHA256 with AES-GCM encryption
5. **SSRF Protection** - Webhook URLs validated against private IP ranges
6. **Tenant Isolation** - Enforced at every service via ProjectAccessClient
6. **JWT** - HS256, 24h expiry, email as subject
7. **OAuth2** - PKCE not implemented (server-side only), state parameter used

---

## Scaling Considerations

| Component | Scaling Strategy |
|-----------|-----------------|
| API Gateway | Horizontal (stateless) |
| Auth Service | Horizontal + Redis session cache |
| Log Ingestion | Horizontal (WebFlux, reactive) |
| Search Service | Horizontal + Elasticsearch cluster |
| Analysis Service | Single instance (scheduled jobs) or leader election |
| Stream Processor | Kafka Streams (partitioned by projectId) |
| Alert Service | Horizontal with outbox pattern |

---

## Ports Summary

| Service | Internal | External (Docker) |
|---------|----------|-------------------|
| API Gateway | 8080 | 8080 |
| Auth Service | 8091 | 8091 |
| Log Ingestion | 8086 | 8086 |
| Search Service | 8084 | 8084 |
| Analysis Service | 8083 | 8083 |
| Stream Processor | 8082 | 8082 |
| Alert Service | 8085 | 8085 |
| PostgreSQL | 5432 | 5432 |
| Redis | 6379 | 6379 |
| Elasticsearch | 9200 | 9200 |
| Kafka | 9092 | 9092 |
| Zookeeper | 2181 | 2181 |
| Frontend (dev) | 5173 | 5173 |