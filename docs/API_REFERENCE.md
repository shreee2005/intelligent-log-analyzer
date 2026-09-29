# Intelligent Log Analyzer - API Reference

## Base URLs

| Environment | Base URL |
|-------------|----------|
| Local Development | `http://localhost:8080` (via API Gateway) |
| Direct Service Access | `http://localhost:{port}` |

---

## Authentication

### API Key (Log Producers)
```
Header: X-API-KEY: <your-api-key>
```
Used for: `/api/v1/logs`, `/api/v1/logs/batch`

### JWT (Dashboard Users)
```
Header: Authorization: Bearer <jwt-token>
```
Used for: All dashboard APIs (`/api/projects`, `/api/v1/search`, `/api/v1/analytics`, `/api/projects/{id}/metrics`, `/api/projects/{id}/webhooks`)

---

## Auth Service APIs (Port 8091)

### Register User
```http
POST /api/auth/register
Content-Type: application/json

{ "email": "user@example.com", "password": "securePassword123" }
```
**Response:** 200 OK `{ "token": "jwt...", "email": "user@example.com" }`

### Login User
```http
POST /api/auth/login
Content-Type: application/json

{ "email": "user@example.com", "password": "securePassword123" }
```
**Response:** 200 OK `{ "token": "jwt...", "email": "user@example.com" }`

### OAuth2 Login
```http
GET /oauth2/authorization/github
GET /oauth2/authorization/google
```
Redirects to provider. On success, redirects to frontend with `#token=JWT&email=...`

---

## Project Management APIs

### Create Project
```http
POST /api/projects
Authorization: Bearer <jwt>
Content-Type: application/json

{ "name": "My Project" }
```
**Response:** 200 OK
```json
{
  "projectId": 1,
  "name": "My Project",
  "apiKey": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```
> **⚠️ IMPORTANT:** `apiKey` is returned ONLY once. Save it immediately. It's hashed in DB and cannot be retrieved.

### List Projects
```http
GET /api/projects
Authorization: Bearer <jwt>
```
**Response:** 200 OK
```json
[
  { "id": 1, "projectId": 1, "name": "My Project" }
]
```

### Validate API Key (for log ingestion)
```http
POST /api/projects/key/validate
X-API-KEY: <api-key>
```
**Response:** 200 OK `{ "projectId": 1 }` or 404 `{ "error": "Invalid API Key" }`

### Check Project Access (internal)
```http
GET /api/projects/{projectId}/access
Authorization: Bearer <jwt>
```
**Response:** 200 OK `{ "projectId": 1 }` or 403 `{ "error": "Project access denied" }`

### Rotate API Key
```http
POST /api/projects/{projectId}/key/rotate
Authorization: Bearer <jwt>
```
**Response:** 200 OK `{ "apiKey": "new-uuid-key" }`
> Invalidates old key immediately. Update your log shippers.

### Revoke API Key
```http
POST /api/projects/{projectId}/key/revoke
Authorization: Bearer <jwt>
```
**Response:** 200 OK `{ "message": "API key revoked" }`
> Stops all log ingestion until rotated.

---

## Log Ingestion APIs (Port 8086 via Gateway)

### Ingest Single Log
```http
POST /api/v1/logs
X-API-KEY: <api-key>
traceparent: 00-<trace-id>-<span-id>-01 (optional)
Content-Type: application/json

{
  "serviceId": "payment-service",
  "level": "ERROR",
  "format": "JSON",
  "message": "Database connection failed",
  "timestamp": "2026-01-15T10:30:00Z"
}
```
**Response:** 202 Accepted
```json
{ "status": "ACCEPTED", "message": "Log accepted for processing", "timestamp": "..." }
```

### Ingest Batch Logs
```http
POST /api/v1/logs/batch
X-API-KEY: <api-key>
Content-Type: application/json

[
  { "serviceId": "svc1", "level": "INFO", "format": "JSON", "message": "msg1", "timestamp": "..." },
  { "serviceId": "svc2", "level": "ERROR", "format": "JSON", "message": "msg2", "timestamp": "..." }
]
```
**Response:** 202 Accepted with Flux stream of responses

---

## Search APIs (Port 8084 via Gateway)

### Search Logs
```http
GET /api/v1/search?projectId=1&query=database+error
Authorization: Bearer <jwt>
```
**Response:** 200 OK `[LogDocument, ...]`

### Search by Service
```http
GET /api/v1/search/service?projectId=1&serviceId=payment-service
Authorization: Bearer <jwt>
```

### Search by Level
```http
GET /api/v1/search/level?projectId=1&level=ERROR
Authorization: Bearer <jwt>
```

### Search by Trace ID
```http
GET /api/v1/search/trace?projectId=1&traceId=abc123
Authorization: Bearer <jwt>
```

### Get Available Services (for metrics dropdown)
```http
GET /api/v1/search/services?projectId=1
Authorization: Bearer <jwt>
```
**Response:** 200 OK `["auth-service", "payment-service", "api-gateway"]`

---

## Analytics APIs (Port 8083 via Gateway)

### Get Live Metrics
```http
GET /api/v1/analytics/metrics/{serviceId}?projectId=1
Authorization: Bearer <jwt>
```
**Response:** 200 OK
```json
{
  "serviceId": "payment-service",
  "totalLogs": 15420,
  "errorCount": 23,
  "warningCount": 156,
  "errorRate": 0.0015,
  "timeline": { "2026-01-15T10:00": 5, "2026-01-15T10:01": 3 }
}
```

---

## Anomaly APIs (Port 8083 via Gateway)

### Get Recent Anomalies
```http
GET /api/projects/{projectId}/anomalies
Authorization: Bearer <jwt>
```
**Response:** 200 OK `[VolumeAnomaly JSON strings, ...]`

---

## Custom Metrics APIs (Port 8091 via Gateway)

### Create Custom Metric
```http
POST /api/projects/{projectId}/metrics
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "name": "HTTP 500 Errors",
  "regexPattern": "HTTP/1\\.1\" 500",
  "serviceId": "api-gateway"
}
```

### List Custom Metrics
```http
GET /api/projects/{projectId}/metrics
Authorization: Bearer <jwt>
```

### Delete Custom Metric
```http
DELETE /api/projects/{projectId}/metrics/{metricId}
Authorization: Bearer <jwt>
```

---

## Webhook APIs (Port 8085 via Gateway)

### Register Webhook
```http
POST /api/projects/{projectId}/webhooks
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "url": "https://your-webhook.example.com/alerts",
  "secret": "webhook-shared-secret",
  "events": ["ANOMALY_DETECTED"]
}
```

### List Webhooks
```http
GET /api/projects/{projectId}/webhooks
Authorization: Bearer <jwt>
```

### Test Webhook
```http
POST /api/projects/{projectId}/webhooks/{subscriptionId}/test
Authorization: Bearer <jwt>
```

### Get Delivery History
```http
GET /api/projects/{projectId}/webhooks/{subscriptionId}/deliveries
Authorization: Bearer <jwt>
```

### Replay Failed Delivery
```http
POST /api/projects/{projectId}/webhooks/deliveries/{deliveryId}/replay
Authorization: Bearer <jwt>
```

### Delete Webhook
```http
DELETE /api/projects/{projectId}/webhooks/{subscriptionId}
Authorization: Bearer <jwt>
```

---

## Data Models

### LogRaw (Ingestion Request)
```json
{
  "serviceId": "string",
  "level": "TRACE|DEBUG|INFO|WARN|ERROR|FATAL",
  "format": "JSON|TEXT",
  "message": "string",
  "timestamp": "ISO-8601 Instant"
}
```

### LogDocument (Elasticsearch)
```json
{
  "id": "uuid",
  "projectId": 1,
  "serviceId": "payment-service",
  "level": "ERROR",
  "message": "Database connection failed",
  "timestamp": "2026-01-15T10:30:00Z",
  "traceId": "abc123",
  "spanId": "span456",
  "parentSpanId": "span789"
}
```

### VolumeAnomaly (Kafka Event)
```json
{
  "schemaVersion": 1,
  "eventType": "ANOMALY_DETECTED",
  "eventId": "uuid",
  "projectId": 1,
  "serviceId": "payment-service",
  "detector": "VOLUME_Z_SCORE",
  "severity": "HIGH|CRITICAL",
  "deduplicationKey": "1:payment-service:volume:2026-01-15T10:30",
  "timestamp": "2026-01-15T10:30:00Z",
  "zScore": 4.52,
  "currentVolume": 1250,
  "meanVolume": 180.5,
  "stdDev": 235.8,
  "type": "SPIKE|DROP"
}
```

### DashboardMetrics (Analytics Response)
```json
{
  "serviceId": "payment-service",
  "totalLogs": 15420,
  "errorCount": 23,
  "warningCount": 156,
  "errorRate": 0.0015,
  "timeline": { "2026-01-15T10:00": 5 }
}
```

### CustomMetric
```json
{
  "id": 1,
  "projectId": 1,
  "name": "HTTP 500 Errors",
  "regexPattern": "HTTP/1\\.1\" 500",
  "serviceId": "api-gateway",
  "createdAt": "2026-01-15T10:00:00Z"
}
```

### WebhookSubscription
```json
{
  "id": 1,
  "projectId": 1,
  "url": "https://webhook.example.com",
  "secret": "encrypted-secret",
  "events": ["ANOMALY_DETECTED"],
  "active": true,
  "createdAt": "2026-01-15T10:00:00Z"
}
```

---

## Error Responses

| Code | Description |
|------|-------------|
| 400 | Bad Request - Invalid input |
| 401 | Unauthorized - Invalid/expired JWT or API Key |
| 403 | Forbidden - No project access |
| 404 | Not Found |
| 409 | Conflict - Duplicate resource |
| 422 | Unprocessable Entity - Validation failed |
| 500 | Internal Server Error |
| 503 | Service Unavailable - Downstream dependency down |

---

## Rate Limits (Recommended)

| Endpoint | Limit |
|----------|-------|
| `/api/v1/logs` | 10,000 req/min per API key |
| `/api/v1/logs/batch` | 100 req/min per API key (max 1000 logs/batch) |
| `/api/v1/search` | 60 req/min per user |
| `/api/v1/analytics/metrics` | 60 req/min per user |
| OAuth2 callbacks | 10 req/min per IP |

---

## PII Redaction Patterns

Applied automatically at ingestion:
- Email addresses: `user@domain.com` → `[EMAIL_REDACTED]`
- JWT tokens: `eyJhbGciOi...` → `[JWT_REDACTED]`
- API Keys: `sk_live_...` / `api_key_...` → `[API_KEY_REDACTED]`
- Passwords: `password=secret` → `password=[REDACTED]`
- Bearer tokens: `Bearer abc123` → `Bearer [REDACTED]`
- IP addresses: `192.168.1.1` → `[IP_REDACTED]`
- Credit cards: `4111 1111 1111 1111` → `[CARD_REDACTED]`