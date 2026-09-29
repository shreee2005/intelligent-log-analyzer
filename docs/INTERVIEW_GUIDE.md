# Interview and Learning Guide

This project is useful in interviews because it demonstrates a real trade-off: fast asynchronous ingestion is separated from slower search, analytics, and alert side effects.

## 1. One-minute explanation

> The platform receives logs through a gateway-protected reactive ingestion service. It authenticates the project API key, adds the server-owned tenant ID, redacts sensitive values, and publishes a normalized event to Kafka. Kafka fan-out lets search, analytics, and stream processing scale independently. Elasticsearch serves project-scoped full-text and trace searches; Redis stores short-lived minute counters and anomaly history; PostgreSQL stores identity and durable webhook delivery state. Anomaly events are normalized and consumed by the alert service, which persists webhook work before making external calls. The current anomaly system is statistical and rule/window based; ML is planned as an evidence-grounded explanation and ranking layer rather than an opaque replacement for detection.

## 2. Why Kafka?

Raw log ingestion should not wait for Elasticsearch, Redis, email, or a customer webhook. Kafka provides:

- buffering when consumers are slower than producers;
- independent consumer groups for independent features;
- replay after a consumer bug or outage;
- partition-based horizontal scaling;
- ordered processing within a partition.

The trade-off is operational complexity and at-least-once duplicates. Kafka is not automatically exactly-once for external side effects.

## 3. Why three storage systems?

### PostgreSQL

Use it for relationships and durable workflow state: users own projects, projects own metrics, subscriptions own deliveries. It supports transactions and uniqueness constraints.

### Elasticsearch

Use it for text search and time-oriented filtering. It is a derived index and can be rebuilt from Kafka if retention/replay policy allows.

### Redis

Use it for fast counters, minute buckets, TTL data, and deduplication markers. Redis should not be the only copy of business-critical events.

## 4. Reactive ingestion versus asynchronous processing

Spring WebFlux and Netty are appropriate at the edge because the service spends time waiting on auth-service and Kafka I/O. Returning `202` avoids holding an HTTP connection while every downstream system completes.

This changes the user contract:

- `202` means the platform accepted the request into the processing path.
- It does not mean search is immediately consistent.
- A production API should expose a request/event ID and processing status if callers need confirmation.

## 5. Multi-tenancy answer

The tenant is the project. The secure path is:

```text
credential -> trusted project ID -> every event/storage key/query -> authorization check
```

The dangerous path would be:

```text
request.projectId -> database query
```

because a caller could change the project ID. This repository correctly overwrites the ingestion project ID after API-key validation and checks ownership for read/write APIs. It is still owner-only rather than organization/team RBAC.

## 6. At-least-once reasoning

Assume a consumer performs a side effect and crashes before its Kafka offset is committed. Kafka will deliver the record again. A correct consumer therefore needs one of:

1. idempotent storage key;
2. event identity table/constraint;
3. atomic transaction that couples offset and side effect;
4. a compensating operation.

The current webhook path uses subscription/event identity to avoid duplicate outbox rows. The search path should use the log event ID as a deterministic Elasticsearch document ID. Redis counter increments need explicit duplicate handling if exact numbers are important.

## 7. Anomaly detection explanation

The volume detector uses:

```text
z = (current volume - historical mean) / historical standard deviation
```

It uses a 24-hour baseline and emits outside ±3 standard deviations. This is easy to explain, cheap, and auditable. It is not sufficient for:

- daily/weekly seasonality;
- new services with little history;
- correlated multi-service incidents;
- concept drift;
- sparse count distributions;
- causal root-cause analysis.

The stream detector and scheduled detector should eventually publish one canonical event contract with detector metadata, baseline window, threshold, and evidence.

## 8. Correct ML strategy

Do not train a model to replace the first-line detector before there is labeled data. A practical sequence is:

1. Keep deterministic detectors for high-recall signal generation.
2. Retrieve related logs, traces, deploys, incidents, and runbooks.
3. Use an existing LLM or smaller classifier to rank hypotheses and explain evidence.
4. Require structured output with evidence IDs and confidence.
5. Capture analyst accept/reject feedback.
6. Evaluate precision, recall, ranking quality, calibration, latency, and cost.
7. Add automation only after approval and rollback controls exist.

This is a hybrid detection + retrieval + explanation architecture. It is safer and more interview-defensible than claiming “AI” for hard-coded UI strings.

## 9. Webhook design answer

Never call an arbitrary customer URL synchronously from a Kafka listener. The current design persists a delivery record first. That gives:

- restart recovery;
- retry state;
- delivery auditability;
- separation of Kafka consumption from external latency.

The production version must add URL allow/deny policy, DNS/IP validation, HTTP timeouts, response-size limits, TLS policy, per-tenant rate limits, and a distributed row-claim lock.

## 10. Distributed tracing answer

`traceId` answers “which request?” `spanId` answers “which operation?” `parentSpanId` answers “who called this operation?” The current project stores correlation identifiers and groups logs. A real waterfall additionally needs:

- span start and end timestamps;
- service/resource name;
- operation name;
- status and error attributes;
- links for asynchronous boundaries;
- sampling information.

OpenTelemetry should instrument HTTP server/client, Kafka producer/consumer, Redis, Elasticsearch, and PostgreSQL. Merely copying a `traceparent` header is not full instrumentation.

## 11. Scaling calculations to discuss

For a rough capacity conversation:

```text
events per second = services * average logs per service per second
daily raw volume = events per second * average event bytes * 86,400
Kafka partitions >= peak events/sec / sustainable consumer events/sec per partition
Redis minute buckets ~= active projects * active services * retention minutes
```

Then ask:

- Is traffic bursty?
- What is the acceptable ingestion-to-search delay?
- How long must logs be retained?
- Which tenants can be noisy?
- Can one tenant exhaust partitions, regex CPU, Redis memory, or Elasticsearch disk?

Tenant quotas, backpressure, sampling, tiered retention, and per-tenant rate limits are required for a SaaS platform.

## 12. Strong trade-off answers

| Question | Good answer |
| --- | --- |
| Why not write directly to Elasticsearch? | Kafka decouples ingestion from search availability and permits replay/fan-out. |
| Why Redis and Elasticsearch? | Redis serves counters quickly; Elasticsearch serves text queries. Their access patterns differ. |
| Why not synchronous webhooks? | Customer latency and failures must not block Kafka consumption. |
| Why API key plus JWT? | Machines ingest with project keys; humans manage projects with user tokens. |
| Is it exactly once? | No; it is primarily at-least-once and requires idempotent consumers. |
| Is anomaly detection ML? | Current detection is statistical/window based. ML is planned for evidence-grounded explanation and ranking. |
| What fails first at scale? | Elasticsearch disk/query cost, Kafka lag, Redis memory, regex CPU, and cross-service authorization latency are likely bottlenecks. |

## 13. Questions a reviewer should ask before production

1. Can a user read another project by changing `projectId`?
2. Can a rotated API key still ingest?
3. What happens when Elasticsearch is down for one hour?
4. Can duplicate Kafka delivery send two webhooks?
5. Can two alert replicas claim the same delivery?
6. How are old logs deleted?
7. How are Kafka schemas evolved?
8. How is a webhook URL prevented from reaching cloud metadata services?
9. How is model output audited and redacted?
10. How does an operator prove the system recovered after a restart?
