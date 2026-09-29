# Delivery Roadmap

## Current position

Completed foundations:

1. Baseline and threat model
2. Tenant authorization foundation
3. Credential lifecycle foundation
4. Gateway boundary
5. Event contract foundation
6. Metrics scalability foundation
7. Anomaly quality foundation
8. Distributed tracing foundation
9. Webhook alerting foundation

The remaining work is grouped below in the order that reduces risk.

## Phase 10 - Evidence-grounded ML suggestions

### Goal

Replace frontend hard-coded advice with a backend service that explains anomalies using evidence from logs, traces, metrics, deployments, incidents, and runbooks.

### Deliverables

1. Create a suggestion service with a model-provider interface.
2. Define an API accepting `projectId`, anomaly ID, trace ID, and a bounded time window.
3. Redact secrets, tokens, credentials, emails, and configured PII before model access.
4. Retrieve similar historical incidents and runbook sections using embeddings.
5. Return structured output: hypotheses, evidence IDs, confidence, uncertainty, recommended actions, and verification steps.
6. Store model version, evidence references, prompt/context hashes, latency, and cost.
7. Add response caching and request rate limits.

### Definition of done

- No suggestion is based only on a free-form prompt.
- Every hypothesis references platform evidence.
- The model cannot execute commands.
- A user can accept, reject, or edit a suggestion.

## Phase 11 - ML evaluation and controlled automation

### Goal

Measure whether suggestions are useful before enabling remediation.

### Deliverables

1. Build a reviewed incident dataset.
2. Measure precision, recall, ranking quality, and confidence calibration.
3. Record analyst feedback and remediation outcomes.
4. Add model, prompt, provider, latency, and cost metrics.
5. Start in recommendation-only mode.
6. Add an allow-listed action catalog and policy engine.
7. Require human approval for destructive actions.
8. Add rollback and post-action verification.

### Definition of done

- Quality thresholds are measured on a fixed evaluation set.
- Low-confidence outputs clearly communicate uncertainty.
- Every action is attributable to a user, policy, model version, and event.

## Phase 12 - Production scale and interview readiness

### Goal

Make the platform operable under failure and explainable in system-design interviews.

### Deliverables

1. Add OpenTelemetry SDK/agents for HTTP, Kafka, Redis, Elasticsearch, and PostgreSQL spans.
2. Define SLOs for ingestion latency, search latency, anomaly latency, and webhook delivery.
3. Add Kafka replication, security, retention, partition planning, and DLQ topics.
4. Add Elasticsearch authentication, index lifecycle management, and shard planning.
5. Add PostgreSQL migrations, backups, restore drills, and connection-pool limits.
6. Add Kubernetes readiness/liveness probes, resource limits, and autoscaling.
7. Run load, soak, restart, duplicate, and dependency-failure tests.
8. Document incident response and disaster recovery.
9. Prepare architecture decision records and interview explanations.

### Definition of done

- A failure of one dependency does not silently lose data.
- Dashboards show SLOs and queue lag.
- Recovery procedures are tested, not just documented.
- The system has a repeatable deployment and rollback path.

## Cross-cutting backlog

- Replace `ddl-auto: update` with Flyway migrations.
- Add Testcontainers integration tests for Kafka, PostgreSQL, Redis, and Elasticsearch.
- Add reusable authorization middleware instead of repeated remote access calls.
- Add webhook SSRF protection, timeout, replay, and delivery-status APIs.
- Add deterministic Elasticsearch document IDs for duplicate-safe indexing.
- Add schema compatibility tests for Kafka events.
- Add team membership and roles.
- Remove legacy raw API-key storage after migration.
