# Development and Handoff Guide

## How to choose the next task

Read these files in order:

1. `docs/IMPLEMENTATION_STATUS.md`
2. `docs/ARCHITECTURE.md`
3. `docs/ROADMAP.md`
4. `docs/OPERATIONS.md`

Do not begin an advanced ML or automation feature while a tenant-isolation or secret-handling regression is open.

## Change workflow

1. Identify the service that owns the behavior.
2. Trace the request/event path across models, controllers, repositories, consumers, and configuration.
3. Write down the tenant boundary and failure behavior before editing.
4. Make the smallest complete change.
5. Add or update a focused test.
6. Run the relevant Maven or npm command.
7. Run an end-to-end smoke test when Kafka or an external store is involved.
8. Update the implementation-status and roadmap documents.

## Service ownership rules

- `auth-service` owns users, projects, API keys, project access, and custom metric definitions.
- `log-ingestion-service` owns external log intake and normalization.
- `stream-processor-service` owns Kafka Streams topology and stream-time anomaly rules.
- `analysis-service` owns Redis analytics and scheduled volume analysis.
- `search-service` owns Elasticsearch documents and search queries.
- `alert-service` owns alert fan-out and webhook delivery state.
- The frontend should call the gateway, not internal service ports.

## Review checklist

### Security

- Is project access checked server-side?
- Is the project ID derived from trusted context where possible?
- Are secrets absent from logs and JSON responses?
- Is a new outbound URL protected from SSRF?
- Are authentication and authorization failures explicit?

### Kafka

- Is the event schema versioned?
- Is the consumer idempotent?
- What happens when deserialization or downstream storage fails?
- Is retry/DLQ behavior defined?
- Does the key preserve the required ordering and tenant locality?

### Data stores

- Is retention explicit?
- Are indexes and queries bounded?
- Can a duplicate event create duplicate business data?
- Does the change require a migration?

### Operations

- Are health, latency, error, lag, retry, and queue-depth metrics available?
- What happens during dependency downtime?
- Can the service restart without losing pending work?
- Is the configuration environment-driven?

## Interview explanation template

For every major feature, explain:

1. The user or system problem.
2. The request/event flow.
3. The data model.
4. The consistency and delivery guarantees.
5. The failure modes.
6. The security boundary.
7. The scaling bottleneck.
8. The trade-off made.
9. How the behavior is tested.
