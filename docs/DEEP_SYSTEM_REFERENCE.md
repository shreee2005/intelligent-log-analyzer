# Deep System Reference

This is the code-independent explanation of the platform. A new engineer should be able to understand the main behavior, contracts, and failure modes without opening every source file. File paths are included so implementation details remain traceable when code changes.

## 1. Mental model

Think of the platform as three pipelines sharing one tenant identity:

```text
                 +--------------------+
Client logs --->| Ingestion pipeline  |---> logs.raw
                 +--------------------+          |
                                                   +--> Search pipeline --> Elasticsearch
                                                   +--> Analytics pipeline --> Redis
                                                   +--> Stream pipeline --> logs.anomalies
                                                               |
                                                               +--> Alert pipeline
                                                                     +--> Email
                                                                     +--> Webhook outbox --> receiver
```

The API gateway is the public front door. PostgreSQL is the system of record for identity and durable webhook work. Kafka is the durable asynchronous transport. Redis is a fast derived-data store, not the source of truth for raw logs. Elasticsearch is a query index, not the source of truth for delivery.

## 2. Repository map

### Root

| Path | Meaning |
| --- | --- |
| `pom.xml` | Maven parent, Java version, Spring Cloud dependency management, module list |
| `docker/docker-compose.yml` | Local dependencies only: Kafka/Zookeeper, Elasticsearch, PostgreSQL, Redis |
| `docker-compose.prod.yml` | Prototype all-in-one deployment; inspect before production use |
| `k8s/` | Kubernetes manifests and deployment experiments |
| `terraform/` | Infrastructure experiments |
| `scripts/` | Utility/startup scripts |
| `test-app/` | Sample producer application |
| `docs/` | Engineering source of truth and learning material |

### Common Spring Boot layout

Each service follows the same boundary:

```text
src/main/java/.../
  *Application.java       process bootstrap
  controller/              HTTP boundary
  service/                 business orchestration
  model/                   transport/domain objects
  repository/              persistence boundary
  kafka/                   event boundary
  config/                  framework configuration
  security/                authentication/authorization clients
src/main/resources/
  application*.yml         environment-specific configuration
src/test/java/              tests
```

Controllers should validate external input and authorization. Services should coordinate business behavior. Repositories should hide storage details. Kafka consumers should deserialize, validate, and call a service; they should not contain a second business implementation.

## 3. End-to-end request: a log

### HTTP request

```http
POST /api/v1/logs
X-API-KEY: <project-key>
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
Content-Type: application/json

{
  "serviceId": "workflow-api",
  "level": "ERROR",
  "format": "JSON",
  "message": "database unavailable",
  "timestamp": "2026-09-16T10:00:00Z"
}
```

### Step-by-step

1. `api-gateway` matches `/api/v1/logs` and forwards to port 8086.
2. `LogIngestionController` extracts `X-API-KEY` and calls auth-service `POST /api/projects/key/validate`.
3. Auth-service hashes the presented key with SHA-256 and looks up the active project. A legacy raw-key lookup still exists for migration compatibility.
4. The returned project ID overwrites any body project ID. This is the critical anti-cross-tenant step.
5. `TraceContext.parse` validates the W3C header shape and copies `traceId` and `parentSpanId`.
6. `LogParserFactory` chooses the parser based on the declared format.
7. `PiiRedactionService` transforms the parsed log before it leaves ingestion.
8. `LogKafkaProducer` publishes the normalized event to `logs.raw`.
9. HTTP returns `202 ACCEPTED`. This means accepted for asynchronous processing, not indexed successfully.
10. Kafka consumers independently update Elasticsearch, Redis, and anomaly state.

### Error semantics

- Missing/invalid API key: `401`.
- Parsing/redaction failure: ingestion returns an error response and publishes a basic error record.
- Kafka send failure: the current service path needs stronger confirmation/error-topic semantics; it must not be described as exactly-once.
- Downstream search/Redis failure: the raw Kafka record remains available for consumer retry depending on listener configuration.

## 4. Log event contract

The exact model classes are:

- Ingestion input: `log-ingestion-service/.../model/LogRaw.java`
- Normalized ingestion event: `.../model/LogEntry.java`
- Stream copy: `stream-processor-service/.../model/LogEntry.java`
- Elasticsearch document: `search-service/.../model/LogDocument.java`

Conceptual fields:

| Field | Producer | Why it exists |
| --- | --- | --- |
| `id` | ingestion | Event identity and UI key |
| `projectId` | auth lookup | Tenant isolation in every consumer |
| `serviceId` | client, validated | Aggregation and filtering dimension |
| `level` | parser | Severity and anomaly input |
| `message` | client | Human-readable search text |
| `timestamp` | client/parser | Event time and minute bucket |
| `format` | client | Parser selection |
| `traceId` | traceparent | Request grouping |
| `spanId` | producer/instrumentation | Operation identity |
| `parentSpanId` | traceparent | Parent-child relationship |

Adding a field requires updating all copies, JSON deserialization, Elasticsearch mapping/query methods, UI assumptions, and contract tests. A field added to only one service silently disappears at the next serialization boundary.

## 5. Kafka topology in detail

### Topics

| Topic | Written by | Read by | Purpose |
| --- | --- | --- | --- |
| `logs.raw` | ingestion | search, analysis, Kafka Streams | canonical normalized log stream |
| `logs.errors` | ingestion error path | currently limited | processing failures |
| `logs.anomalies` | stream processor and analysis service | alert, analysis history | anomaly events |

### Delivery guarantee

The effective design is at-least-once. A consumer can receive the same record again after a crash between the side effect and offset commit. Therefore:

- Elasticsearch indexing should use a stable event ID as the document ID.
- Redis increments need a deduplication strategy if exact counts matter.
- Anomaly publication uses a Redis TTL deduplication key.
- Webhook enqueueing uses subscription ID + event ID uniqueness logic.

### Partitioning

The ingestion producer currently uses service identity as the Kafka key. This helps preserve order per service but does not guarantee a tenant cannot be interleaved with another tenant using the same service name. Production partition strategy should use a composite key such as `projectId:serviceId` if per-project/service ordering is required.

### What is missing

The repository does not yet provide a complete retry-topic and dead-letter-topic workflow. A failed JSON record is logged, but “logged” is not “recoverable.” The production design should include:

```text
logs.raw -> consumer retry topic (backoff) -> consumer DLQ
                                      \-> alert/metric on DLQ growth
```

## 6. Stream processor versus analysis service

There are two anomaly paths by design:

### Kafka Streams path

`LogStreamProcessor`:

1. Reads `logs.raw`.
2. Runs `LogMetricEvaluator` for custom regex metrics.
3. Groups by stream key.
4. Uses one-minute `TimeWindows`.
5. Builds `LogAggregator`.
6. Calls `AnomalyDetector`.
7. Publishes anomalies to `logs.anomalies`.

This path is event-time/window oriented and can react quickly.

### Scheduled Redis path

`AnalyticsKafkaConsumer` writes Redis minute buckets. `AnomalyDetectionService` wakes every minute:

1. Gets active `service_buckets` indexes.
2. Retrieves the last 24 hours of volume values.
3. Calculates mean and population standard deviation.
4. Reads the completed prior minute.
5. Calculates `(current - mean) / stdDev`.
6. Emits a `DROP` or `SPIKE` when the score is outside ±3.
7. Skips low-volume baselines and deduplicates the event.

This is statistical detection, not machine learning. The log line in the service saying “ML” is inaccurate and should be changed when the code is next edited.

## 7. Redis key design

| Key | Type | Meaning | Lifetime |
| --- | --- | --- | --- |
| `metrics:{project}:{service}:{utc-minute}` | Hash | `TOTAL`, `ERROR`, `WARN` counters | 24 hours |
| `service_buckets:{project}:{service}` | Set | minute buckets for a project/service | 25 hours |
| `service_bucket_indexes` | Set | known service bucket keys | 25 hours |
| `anomalies:{project}` | List | last 50 anomaly payloads | 7 days |
| `anomaly_dedup:{identity}` | String | duplicate suppression marker | 24 hours |

The maintained index avoids Redis `KEYS`, which would scan the entire keyspace. However, incrementing a counter, adding the bucket to an index, and expiring multiple keys are not one atomic transaction. High-scale correctness work should use a Lua script or pipelining.

## 8. Elasticsearch model and search isolation

`LogDocument` is indexed into the `logs` index. Search methods always include project ID:

- message search: `/api/v1/search?projectId=&query=`
- service search: `/api/v1/search/service?projectId=&serviceId=`
- level search: `/api/v1/search/level?projectId=&level=`
- trace search: `/api/v1/search/trace?projectId=&traceId=`

The controller first calls auth-service `/{projectId}/access`, then executes the repository query. This is defense in depth: authorization happens before querying, and the query also filters project ID.

The current controller uses `@CrossOrigin(origins = "*")`; production should move CORS to the gateway and remove broad service-level exposure.

## 9. Authentication and project access

There are two credentials with different jobs:

| Credential | Used by | Where accepted | Lifetime |
| --- | --- | --- | --- |
| JWT bearer token | dashboard user | project/search/analytics/webhook APIs | configurable token lifetime |
| `X-API-KEY` | external log producer | ingestion only | until revoked/rotated |

User flow:

1. Register/login at auth-service.
2. JWT is returned to the frontend.
3. Frontend stores it in local storage and sends `Authorization: Bearer ...`.
4. Auth-service resolves the authenticated email.
5. Project ownership is checked against the requested project.

The current frontend local-storage token design is convenient for a demo but vulnerable to XSS token theft. A production browser architecture should use a secure HttpOnly cookie or a backend-for-frontend session.

## 10. Webhook lifecycle

```text
Kafka anomaly
   |
   v
AlertKafkaConsumer
   |
   +-- email immediately
   |
   +-- WebhookService.enqueue
           |
           +-- WebhookSubscription lookup by project
           +-- duplicate check(subscription,event)
           +-- WebhookDelivery PENDING in PostgreSQL
                         |
                         v
                WebhookDeliveryScheduler
                         |
                         +-- decrypt AES-GCM secret
                         +-- HMAC-SHA256 exact JSON body
                         +-- HTTP POST
                         +-- DELIVERED or retry/FAILED
```

The Kafka listener does not wait for the external receiver. This protects Kafka consumption from slow or unavailable customers. The delivery table is the durable queue.

Current gaps:

- `RestClient` needs explicit connect/read timeouts.
- A URL can target internal network addresses; SSRF validation is required.
- Multiple alert-service replicas can select the same pending row.
- There is no user-facing delivery history/replay endpoint.
- Schema creation relies on Hibernate update instead of migration files.

## 11. Frontend behavior

`App.jsx` owns session/project selection and chooses the dashboard tab. `config.js` defines the single gateway base URL. `LogSearchConsole.jsx` loads project-scoped search results and groups records sharing `traceId`; records without a trace remain standalone. `MetricsDashboard.jsx` manages custom metric CRUD and polls data. `IntegrationHub.jsx` generates producer examples.

Current frontend caveats:

- `LiveMetricsPanel.jsx` contains a hard-coded service selection comment and should obtain services from project data or a service endpoint.
- `LogSemanticParser.js` generates deterministic text suggestions. It is not an ML model and must not be presented as one.
- The UI has trace grouping, not a complete distributed waterfall because span duration/start/end metadata is absent.

## 12. Configuration truth

### Local application ports

| Process | Port |
| --- | ---: |
| gateway | 8080 |
| stream processor | 8082 |
| analysis | 8083 |
| search | 8084 |
| alert | 8085 |
| ingestion | 8086 |
| auth | 8091 |
| dashboard dev server | Vite default |

### Important configuration names

| Name | Consumer |
| --- | --- |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | ingestion, stream, analysis, search, alert |
| `SPRING_DATA_REDIS_HOST/PORT` | analysis, stream, auth |
| `SPRING_ELASTICSEARCH_URIS` | search |
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | PostgreSQL services |
| `JWT_SECRET` | auth |
| `WEBHOOK_ENCRYPTION_KEY` | alert |
| `AUTH_SERVICE_URL` | ingestion |
| `*_SERVICE_URL` | gateway route targets |

The prototype `docker-compose.prod.yml` currently uses names such as `LOG_INGESTION_URL`, while the gateway configuration expects `LOG_INGESTION_SERVICE_URL`; this must be reconciled before deployment. It also maps some service container ports inconsistently. Treat that compose file as an audit target, not a verified production deployment.
