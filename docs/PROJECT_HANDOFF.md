# Project Handoff for Future Developers and AI Agents

## What this file is for

This is the first file to read before changing the project. It answers four questions:

1. What is this project?
2. What has already been implemented?
3. What is incomplete or unsafe?
4. Which files should be inspected for the next change?

The detailed documents linked below are the source of truth. Do not infer implementation status from the resume description or old progress notes.

## Current project stage

The project is at the **multi-tenant observability foundation stage**.

### Completed foundations

| Phase | Capability | Current state |
| --- | --- | --- |
| 1 | Baseline and threat model | Completed |
| 2 | Project-level authorization | Implemented, owner-based |
| 3 | API-key lifecycle | Hashing, rotation, revocation implemented; legacy raw-key migration remains |
| 4 | API gateway boundary | Implemented for browser-facing routes |
| 5 | Kafka anomaly contract | Common metadata implemented; full retry/DLQ remains |
| 6 | Redis metric scalability | Maintained indexes and TTLs implemented |
| 7 | Volume/error anomaly detection | Statistical detectors implemented |
| 8 | Trace correlation | `traceparent`, trace fields, and trace search implemented |
| 9 | Durable webhook alerting | PostgreSQL queue, encryption, signing, retry worker implemented |

### Current honest capability

The platform can:

- accept project-authenticated logs;
- attach the trusted project ID;
- redact configured sensitive values;
- publish normalized logs to Kafka;
- index logs into Elasticsearch;
- aggregate metrics in Redis;
- detect statistical/window anomalies;
- search logs by project, service, level, and trace;
- group trace-correlated logs in the dashboard;
- notify by email;
- persist and retry signed webhook deliveries.

### It cannot honestly claim yet

- Full ML-based root-cause suggestions.
- Full OpenTelemetry distributed tracing.
- A complete trace waterfall with span durations.
- Production-grade Kafka retry/DLQ processing.
- Team/organization RBAC.
- Production-ready webhook security.
- HA production deployment.

## Read in this order

1. [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) - phase status and validation evidence.
2. [ARCHITECTURE.md](ARCHITECTURE.md) - service boundaries and high-level flows.
3. [DEEP_SYSTEM_REFERENCE.md](DEEP_SYSTEM_REFERENCE.md) - exact execution paths and data behavior.
4. [FILE_REFERENCE.md](FILE_REFERENCE.md) - source-file ownership map.
5. [API_REFERENCE.md](API_REFERENCE.md) - HTTP and Kafka-facing contracts.
6. [ROADMAP.md](ROADMAP.md) - remaining work and definitions of done.
7. [OPERATIONS.md](OPERATIONS.md) - setup, tests, smoke checks, and failure checks.
8. [INTERVIEW_GUIDE.md](INTERVIEW_GUIDE.md) - design reasoning and trade-offs.
9. [CONTRIBUTING.md](CONTRIBUTING.md) - safe change workflow.

## How to continue a feature

### Step 1: classify the request

Place the request into one of these areas:

- Identity/tenancy: `auth-service`
- External log intake: `log-ingestion-service`
- Kafka Streams processing: `stream-processor-service`
- Redis analytics and scheduled anomalies: `analysis-service`
- Elasticsearch indexing/search: `search-service`
- Alerts and external delivery: `alert-service`
- Browser routing: `api-gateway` and `frontend-dashboard`
- Deployment: `docker-compose*.yml`, `k8s/`, or `terraform/`

### Step 2: follow the existing execution chain

For a backend feature, inspect in this order:

```text
controller -> service -> model -> repository/client -> configuration -> test
```

For an event feature, inspect:

```text
producer -> topic/config -> consumer group -> model/Serde -> side effect -> retry/idempotency
```

For a frontend feature, inspect:

```text
App.jsx -> component -> config.js -> gateway route -> backend controller
```

### Step 3: check the tenant boundary

Every project-scoped operation must answer:

1. Where did `projectId` come from?
2. Was it derived from a trusted API key or authorized JWT?
3. Is access checked before the query/mutation?
4. Does the storage query include project ID?
5. Can the response reveal another tenant's data?

### Step 4: check delivery behavior

Every Kafka side effect must answer:

- What happens on deserialization failure?
- What happens if the downstream store is unavailable?
- Can the message be delivered twice?
- What is the idempotency key?
- Is there a retry topic, database retry state, or DLQ?
- Can operators see lag and failures?

### Step 5: update documentation

After implementation, update:

- `IMPLEMENTATION_STATUS.md` for actual status and validation.
- `API_REFERENCE.md` for endpoint/event changes.
- `FILE_REFERENCE.md` if new ownership files are added.
- `ARCHITECTURE.md` if a service/data flow changes.
- `ROADMAP.md` if a phase or dependency changes.

## Recommended next implementation sequence

### Next 1: production correctness gates

Before ML, close the reliability/security gaps:

1. Add Flyway migrations for auth and webhook tables.
2. Add Testcontainers integration tests.
3. Add deterministic Elasticsearch IDs.
4. Add webhook SSRF validation and HTTP timeouts.
5. Add database row claiming/distributed locking for webhook workers.
6. Add Kafka retry topics and DLQs.
7. Fix production compose variable and port mismatches.

Relevant files:

- `alert-service/src/main/java/com/loganalyzer/alert/service/WebhookService.java`
- `alert-service/src/main/java/com/loganalyzer/alert/model/WebhookDelivery.java`
- `search-service/src/main/java/com/loganalyzer/search/kafka/SearchKafkaConsumer.java`
- `search-service/src/main/java/com/loganalyzer/search/model/LogDocument.java`
- `docker-compose.prod.yml`
- service `pom.xml` files

### Next 2: real ML suggestion service

Do not add more hard-coded suggestions to `LogSemanticParser.js`. Build a backend evidence pipeline:

```text
anomaly ID/trace ID
    -> retrieve logs, metrics, traces, deploys, incidents, runbooks
    -> redact sensitive data
    -> model provider
    -> structured hypotheses/evidence/confidence/actions
    -> user feedback and audit record
```

Recommended new service:

```text
ml-suggestion-service/
  controller/
  service/
  model/
  retrieval/
  provider/
  security/
  repository/
```

First version should be recommendation-only. It must not execute shell commands or restart infrastructure.

Relevant existing files:

- `analysis-service/src/main/java/com/loganalyzer/analysis/model/VolumeAnomaly.java`
- `analysis-service/src/main/java/com/loganalyzer/analysis/controller/AnomalyController.java`
- `frontend-dashboard/src/LogSearchConsole.jsx`
- `frontend-dashboard/src/LogSemanticParser.js`

### Next 3: complete tracing

Add OpenTelemetry instrumentation and persist:

- span start/end;
- operation name;
- service/resource name;
- status;
- error attributes;
- parent-child links across Kafka.

Then add a trace endpoint returning a span tree and implement the waterfall UI.

Relevant existing files:

- `log-ingestion-service/src/main/java/com/loganalyzer/ingestion/tracing/TraceContext.java`
- `log-ingestion-service/src/main/java/com/loganalyzer/ingestion/controller/LogIngestionController.java`
- `search-service/src/main/java/com/loganalyzer/search/model/LogDocument.java`
- `search-service/src/main/java/com/loganalyzer/search/controller/SearchController.java`
- `frontend-dashboard/src/LogSearchConsole.jsx`

## Validation expected for every change

At minimum:

1. Run `git diff --check`.
2. Run the smallest affected Maven test/package command.
3. Run frontend build if frontend files changed.
4. Test project isolation with two users/projects.
5. Test duplicate/retry behavior for event changes.
6. Update the status documentation with what was actually verified.

Do not mark a feature complete merely because the application compiles. A feature is complete only when its success path, failure path, tenant boundary, persistence behavior, and operational behavior are understood and tested.

## Status vocabulary

Use these exact labels:

- **Implemented:** code path exists and targeted validation passed.
- **Partially implemented:** core path exists but production-critical pieces are missing.
- **Unverified:** implementation exists but required infrastructure/integration validation was unavailable.
- **Planned:** no working implementation exists.

## Handoff rule

When another model receives a future task, it should:

1. Read this file.
2. Read the relevant detailed document.
3. Inspect only the linked source files for the requested area.
4. Preserve unrelated dirty changes.
5. Implement, validate, and update documentation.

This prevents repeated full-repository rediscovery while keeping architectural decisions and remaining work visible.
