# File-by-File Reference

This index explains the purpose of every important application file. It is intentionally organized by execution path rather than alphabetically.

## Root and deployment

| File/folder | Responsibility | Read when |
| --- | --- | --- |
| `pom.xml` | Declares all seven Maven modules, Java 21, Spring Cloud BOM, and Kafka version | Adding a service or dependency |
| `docker/docker-compose.yml` | Starts local infrastructure without application containers | Running dependencies locally |
| `docker-compose.prod.yml` | Prototype container deployment; currently has port and variable mismatches | Auditing deployment |
| `k8s/` | Kubernetes manifests/experiments | Designing cluster deployment |
| `terraform/` | Infrastructure provisioning experiments | Moving to cloud infrastructure |
| `test-app/` | Sample application for generating logs | Testing ingestion |

## auth-service

### Bootstrap, security, and configuration

| File | Role |
| --- | --- |
| `AuthServiceApplication.java` | Spring Boot process entry point |
| `SecurityConfig.java` | HTTP authorization, JWT filter placement, OAuth2 login, CORS/security defaults |
| `JwtAuthenticationFilter.java` | Reads bearer tokens and places the authenticated identity in Spring Security context |
| `JwtUtil.java` | Creates/parses JWT claims and validates the configured signing secret |
| `OAuth2ClientConfig.java` | Provider registration/configuration for Google/GitHub |
| `OAuth2SuccessHandler.java` | Handles successful provider login and redirects with token handoff |
| `ApiKeyHasher.java` | SHA-256 normalization of newly stored API keys |
| `application.yml` | PostgreSQL, Redis, JWT, and server settings |

### HTTP and domain

| File | Role |
| --- | --- |
| `AuthController.java` | Register and login endpoints |
| `ProjectController.java` | Create/list projects, validate API keys, check access, rotate/revoke keys |
| `CustomMetricController.java` | Create/list/delete project-owned regex metrics and fetch metric data |
| `User.java` | User identity and credential persistence model |
| `Project.java` | Project tenant model, owner relation, key fields, active flag |
| `Role.java` | Role enum/domain value; team-level authorization is not complete |
| `CustomMetric.java` | Project-owned metric name and regex definition |

### Repositories

| File | Role |
| --- | --- |
| `UserRepository.java` | User lookup by email/identity |
| `ProjectRepository.java` | Owner queries, hashed/raw API-key lookup, project lookup |
| `CustomMetricRepository.java` | Project-scoped metric persistence |

## log-ingestion-service

| File | Role |
| --- | --- |
| `LogIngestionServiceApplication.java` | Service bootstrap |
| `LogIngestionController.java` | Reactive HTTP boundary, API-key validation, batch handling, traceparent extraction |
| `GlobalExceptionHandler.java` | Converts validation/controller errors to HTTP responses |
| `LogIngestionService.java` | Parser -> redaction -> Kafka orchestration |
| `LogKafkaProducer.java` | Publishes normalized events to `logs.raw` and basic errors to `logs.errors` |
| `KafkaTopicConfig.java` | Topic creation/configuration for local operation |
| `IngestionMetrics.java` | Micrometer counters/timers for ingestion behavior |
| `LogRaw.java` | External request shape before normalization |
| `LogEntry.java` | Canonical event shape sent to Kafka |
| `LogIngestionResponse.java` | Accepted/error response returned to the caller |
| `LogLevel.java` | Severity vocabulary |
| `LogFormat.java` | Parser selection vocabulary |
| `LogParser.java` | Parser interface |
| `JsonLogParser.java` | JSON log normalization implementation |
| `LogParserFactory.java` | Chooses parser by format |
| `PiiRedactionService.java` | Removes configured sensitive values before publication |
| `TraceContext.java` | Validates/parses W3C traceparent into trace and parent span IDs |
| `application*.yml` | Broker, auth-service URL, and port profiles |

## stream-processor-service

| File | Role |
| --- | --- |
| `StreamProcessorServiceApplication.java` | Kafka Streams/Spring bootstrap |
| `KafkaStreamsConfig.java` | Streams application ID, broker, default serialization/runtime |
| `TopicsConfig.java` | Canonical raw/anomaly topic names |
| `LogStreamProcessor.java` | Topology: read -> custom metric peek -> group/window -> aggregate -> detect -> publish |
| `LogMetricEvaluator.java` | Loads custom metric definitions and increments Redis counters on regex matches |
| `AnomalyDetector.java` | Stream-window anomaly decision logic |
| `LogAggregator.java` | Window state: counts by service/severity |
| `Anomaly.java` | Stream detector anomaly event shape |
| `CustomMetric.java` | Local copy of metric configuration |
| `LogEntry.java` | Local Kafka input shape |
| `LogLevel.java` | Local severity enum |
| `CustomMetricRepository.java` | Reads metric definitions from PostgreSQL |
| `JsonSerdeFactory.java` | Jackson-based Kafka Streams serializer/deserializer |

## analysis-service

| File | Role |
| --- | --- |
| `AnalysisServiceApplication.java` | Service bootstrap |
| `AnalyticsController.java` | Project-authorized metric query API |
| `AnomalyController.java` | Project-authorized anomaly history API |
| `AnalyticsKafkaConsumer.java` | Consumes raw logs into Redis and consumes anomaly events for history |
| `MetricsRedisRepository.java` | Redis key/index/counter/history/deduplication implementation |
| `AnomalyDetectionService.java` | Scheduled 24-hour z-score detector |
| `DashboardMetrics.java` | Aggregated dashboard response |
| `VolumeAnomaly.java` | Statistical anomaly event shape |
| `ProjectAccessClient.java` | Calls auth-service before project-scoped operations |
| `RedisConfig.java` | Redis serialization/client configuration |
| `CorsConfig.java` | Service CORS configuration; gateway centralization is preferred |

## search-service

| File | Role |
| --- | --- |
| `SearchServiceApplication.java` | Service bootstrap |
| `SearchKafkaConsumer.java` | Converts `logs.raw` events to Elasticsearch documents |
| `SearchController.java` | Project-authorized query endpoints |
| `LogDocument.java` | Elasticsearch mapping including project and trace fields |
| `LogSearchRepository.java` | Elasticsearch query methods |
| `DataRetentionScheduler.java` | Scheduled deletion/retention behavior |
| `ProjectAccessClient.java` | Auth-service ownership check |
| `JacksonConfig.java` | JSON mapping compatibility |

## alert-service

| File | Role |
| --- | --- |
| `AlertServiceApplication.java` | Service bootstrap and scheduling |
| `AlertKafkaConsumer.java` | Reads anomalies, filters high/critical events, invokes email/webhook paths |
| `AnomalyEvent.java` | Common alert-side event deserialization model |
| `NotificationService.java` | Email notification behavior |
| `WebhookController.java` | Project-authorized webhook create/disable endpoints |
| `WebhookService.java` | Enqueue, idempotency, due selection, HTTP delivery, retry state |
| `WebhookDeliveryScheduler.java` | Periodic worker that calls due delivery logic |
| `WebhookSubscription.java` | Destination configuration and encrypted secret |
| `WebhookDelivery.java` | Durable outbox row and state machine |
| `WebhookSubscriptionRepository.java` | Active subscription lookup |
| `WebhookDeliveryRepository.java` | Due-delivery and duplicate checks |
| `WebhookSignatureService.java` | HMAC-SHA256 body signatures |
| `WebhookSecretCrypto.java` | AES-256-GCM encryption/decryption at rest |
| `ProjectAccessClient.java` | Owner authorization through auth-service |
| `JacksonConfig.java` | Event JSON compatibility |

## api-gateway

| File | Role |
| --- | --- |
| `ApiGatewayApplication.java` | Gateway bootstrap |
| `application*.yml` | Route predicates and environment-based target URLs |
| `CorsConfig.java` | Gateway CORS policy |

## frontend-dashboard

| File | Role |
| --- | --- |
| `main.jsx` | React root mount |
| `App.jsx` | Authentication state, project hub, navigation, dashboard composition |
| `config.js` | `VITE_API_BASE_URL`; defaults to gateway |
| `LogSearchConsole.jsx` | Search results, severity rendering, trace grouping accordion |
| `MetricsDashboard.jsx` | Custom metric creation/deletion and time-series polling |
| `LiveMetricsPanel.jsx` | Service metric summary and live polling |
| `IntegrationHub.jsx` | Generates integration snippets for clients |
| `LogSemanticParser.js` | Current deterministic text suggestions; not an ML implementation |
| `App.css` | Component layout/theme styles |
| `index.css` | Global styles |

## Tests and what they currently prove

The current `*ApplicationTests` classes primarily prove that a Spring context can start when dependencies/configuration are available. They do not prove full cross-service flows. The next test layer should add:

1. Controller tests for 401/403 project isolation.
2. Repository tests for hashed key lookup and duplicate webhook identity.
3. Testcontainers Kafka tests for consumer restart/replay.
4. Elasticsearch indexing/search tests.
5. Redis bucket and z-score tests including missing zero buckets.
6. Webhook receiver tests for signatures, retries, timeout, and restart persistence.
7. Browser/API smoke tests for login -> project -> search -> metrics.
