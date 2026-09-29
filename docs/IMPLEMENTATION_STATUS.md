# Implementation Status

This file is the current engineering truth for what is implemented, what was verified, and what still needs work.

## Status legend

- **Implemented:** code exists and the intended path is wired.
- **Partially implemented:** the core path exists but important production pieces are missing.
- **Unverified:** code exists but the required infrastructure or integration test was not available.
- **Planned:** not implemented yet.

## Phase matrix

| Phase | Area | Status | What exists | Main gaps |
| --- | --- | --- | --- | --- |
| 1 | Baseline and threat model | Implemented | Service/data-flow review and release gates | Keep this document updated as architecture changes |
| 2 | Tenant authorization | Partially implemented | Owner checks for metrics, search, analytics, anomalies, and webhooks | Organization/team membership, reusable policy module, negative integration tests |
| 3 | Credential lifecycle | Implemented | Hashed API-key lookup, rotation, revocation, redaction. Legacy raw key column removed. | Expiry policy, audit history, secure refresh sessions |
| 4 | Gateway boundary | Implemented foundation | Gateway routes, frontend base URL, and reconciled docker-compose environment variables | Gateway authentication/rate limiting, service-to-service identity |
| 8.5 | Technical debt & security cleanup | Implemented | Dynamic Live Metrics service selector, SSRF webhook validation, RestClient timeouts, Webhook Management UI, API Key Rotate/Revoke UI | None |
| Auth-fixes | Auth service login bugs | **Implemented (Sept 2026)** | Flyway V1 migration added; `application.yml` JWT/OAuth2 env defaults fixed; `ProjectController.list()` LazyInitializationException fixed via explicit DTO projection; `@Transactional` added to all LAZY-owner methods; OAuth2 redirect URL made configurable via `app.frontend-url` | None |
| 9 | Webhook alerting | Partially implemented | PostgreSQL delivery queue, HMAC signing, encrypted secrets, retry worker, SSRF protection, Webhook UI tab | Flyway migrations, delivery history & replay API, DLQ table |
| 10 | ML suggestion service | Planned | Frontend contains deterministic suggestion text only | Backend model gateway, retrieval, redaction, evidence, feedback |
| 11 | ML evaluation and automation | Planned | No evaluated model or controlled action runner | Labeled dataset, quality metrics, approval policy, rollback |
| 12 | Production scale | Planned | Local Docker infrastructure and service actuator endpoints | HA Kafka, secure Elasticsearch, SLOs, load tests, DR, Kubernetes hardening |

## Verified build evidence

The following validations were run during implementation:

- Auth service package/test build passed during authorization and credential changes.
- Log-ingestion service package/test build passed.
- Search service package compilation passed. Its context test requires a running Elasticsearch instance.
- Analysis service tests passed after the Redis configuration correction.
- Stream processor package compilation passed.
- Alert service package compilation passed.
- API gateway tests passed.
- Frontend production build passed with `npm run build`.

The alert-service Spring context test was blocked in one local run because PostgreSQL did not contain the configured `admin` role. That is an environment failure, not proof that the database-backed alert path is integration-tested.

## Known correctness and production risks

### High priority

1. Project authorization is implemented through repeated HTTP calls to auth-service rather than a shared gateway/service authorization component.
2. The alert webhook controller validates ownership, but webhook URLs still need SSRF protection.
3. Alert-service uses `ddl-auto: update`; schema migrations should be introduced before production.
4. The webhook worker has no distributed lock or claim protocol for multiple alert-service replicas.
5. The local Kafka deployment has one broker and replication factor one.
6. Some service configuration still contains permissive or local-only settings such as trusted packages and exposed actuator details.

### Medium priority

1. Full OpenTelemetry instrumentation is not present.
2. Search document IDs are not yet clearly deterministic across duplicate Kafka deliveries.
3. The dashboard still has hard-coded service selection in the live metrics panel.
4. The anomaly pipeline has two detectors and needs one documented event lifecycle.
5. Existing repository changes are not yet represented by a clean release baseline or migration history.

## Production readiness decision

The project is suitable for continued development and demonstrations. It is not ready to be called production-grade SaaS until the high-priority risks above are addressed and integration tests run against Kafka, Redis, PostgreSQL, and Elasticsearch.
