package com.loganalyzer.ml.service;

import com.loganalyzer.ml.model.IncidentEmbedding;
import com.loganalyzer.ml.repository.IncidentEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentEmbeddingService implements CommandLineRunner {

    private final IncidentEmbeddingRepository repository;
    private final EmbeddingModel embeddingModel;

    @Value("${ml.suggestion.rag.embedding-model:text-embedding-3-small}")
    private String embeddingModelName;

    @Override
    public void run(String... args) {
        seedIncidentsIfEmpty();
    }

    @Transactional
    public void seedIncidentsIfEmpty() {
        long count = repository.count();
        if (count > 0) {
            log.info("Incident embeddings already exist ({} records), skipping seed", count);
            return;
        }

        log.info("Seeding incident embeddings...");

        List<IncidentEmbedding> incidents = createSeedIncidents();
        for (IncidentEmbedding incident : incidents) {
            try {
                float[] embedding = embedText(incident.getDescription());
                incident.setEmbedding(embedding);
                repository.save(incident);
                log.debug("Seeded incident: {}", incident.getIncidentId());
            } catch (Exception e) {
                log.warn("Failed to embed incident {}: {}", incident.getIncidentId(), e.getMessage());
            }
        }

        log.info("Successfully seeded {} incident embeddings", incidents.size());
    }

    private List<IncidentEmbedding> createSeedIncidents() {
        return List.of(
                // Database connection pool exhaustion
                IncidentEmbedding.builder()
                        .incidentId("INC-2025-03-14-db-pool")
                        .title("Database Connection Pool Exhaustion")
                        .description("Service payment-service experienced massive spike in database connection pool usage. All connections exhausted, causing cascading timeout errors across dependent services. Root cause: missing connection leak detection and improper pool sizing for peak traffic.")
                        .resolution("Increased HikariCP max pool size from 20 to 50. Added connection leak detection threshold (30s). Implemented circuit breaker on downstream calls. Added pool metrics monitoring.")
                        .servicePatterns(new String[]{"database", "connection-pool", "hikaricp", "postgresql"})
                        .errorPatterns(new String[]{"timeout", "pool-exhausted", "connection-leak", "cascading-failure"})
                        .build(),

                // Retry storm cascade
                IncidentEmbedding.builder()
                        .incidentId("INC-2025-01-22-retry-storm")
                        .title("Retry Storm Cascade Failure")
                        .description("Service inventory-service experienced latency spike. Upstream services (order-service, payment-service) had aggressive retry policies without exponential backoff or circuit breakers. Retry amplification caused 100x traffic increase, overwhelming the already struggling service.")
                        .resolution("Implemented exponential backoff with jitter (base 100ms, max 5s). Added circuit breaker pattern (resilience4j). Set max retry attempts to 3. Added bulkhead pattern for thread pool isolation.")
                        .servicePatterns(new String[]{"retry-storm", "circuit-breaker", "resilience4j", "inventory"})
                        .errorPatterns(new String[]{"retry-amplification", "cascading-failure", "latency-spike", "timeout"})
                        .build(),

                // Memory leak causing OOM
                IncidentEmbedding.builder()
                        .incidentId("INC-2025-02-10-oom-leak")
                        .title("Memory Leak Leading to OOM Kills")
                        .description("Service notification-service gradually increased heap usage over 48 hours until OOM killer terminated the process. Heap dump revealed accumulating MetricRegistry objects from Dropwizard Metrics not being cleaned up. Each request created new Timer/Histogram instances.")
                        .resolution("Fixed MetricRegistry reuse - use shared instance. Added JVM heap monitoring alert at 80% usage. Implemented periodic heap dump on high memory. Upgraded Dropwizard Metrics to version with leak fix.")
                        .servicePatterns(new String[]{"memory-leak", "oom", "jvm", "notification", "dropwizard"})
                        .errorPatterns(new String[]{"out-of-memory", "heap-exhaustion", "metric-registry-leak", "garbage-collection"})
                        .build(),

                // Kafka consumer lag
                IncidentEmbedding.builder()
                        .incidentId("INC-2024-12-05-kafka-lag")
                        .title("Kafka Consumer Lag Causing Processing Delay")
                        .description("Analysis service consumer group fell behind by 2M messages. Root cause: synchronous HTTP calls to external API in consumer loop with 5s timeout. Under load, processing time exceeded poll interval, causing rebalances and further lag.")
                        .resolution("Moved external API calls to async CompletableFuture with bounded queue. Increased consumer poll interval. Added max.poll.records=500. Implemented dead letter queue for failed messages. Added consumer lag alert at 100k messages.")
                        .servicePatterns(new String[]{"kafka", "consumer-lag", "analysis-service", "rebalance"})
                        .errorPatterns(new String[]{"consumer-lag", "rebalance", "poll-timeout", "backpressure"})
                        .build(),

                // Thread pool exhaustion
                IncidentEmbedding.builder()
                        .incidentId("INC-2025-04-18-thread-pool")
                        .title("Thread Pool Exhaustion in API Gateway")
                        .description("API Gateway thread pool exhausted during traffic spike. All 200 threads blocked on downstream service calls with 30s timeout. No circuit breaker or bulkhead pattern. Thread pool queue grew to 10k requests, causing cascade failure.")
                        .resolution("Implemented bulkhead pattern with separate thread pools per downstream service. Added circuit breaker (resilience4j) with 50% failure rate threshold. Reduced default timeout to 5s. Added thread pool queue size limit (100).")
                        .servicePatterns(new String[]{"thread-pool", "bulkhead", "api-gateway", "circuit-breaker"})
                        .errorPatterns(new String[]{"thread-exhaustion", "queue-overflow", "timeout-cascade", "blocked-threads"})
                        .build(),

                // Disk space exhaustion
                IncidentEmbedding.builder()
                        .incidentId("INC-2024-11-30-disk-space")
                        .title("Disk Space Exhaustion from Log Rotation Failure")
                        .description("Log rotation cron job failed silently for 3 days. Application logs grew to 500GB, filling root partition. Database and Kafka also on same partition caused complete system halt. Monitoring didn't alert on disk usage.")
                        .resolution("Added logrotate configuration with compression and max size. Implemented disk usage alert at 70% and critical at 85%. Separated logs, database, and Kafka to different partitions. Added log rotation health check.")
                        .servicePatterns(new String[]{"disk-space", "log-rotation", "infrastructure", "monitoring"})
                        .errorPatterns(new String[]{"disk-full", "no-space-left", "log-rotation-failure", "partition-full"})
                        .build(),

                // Certificate expiration
                IncidentEmbedding.builder()
                        .incidentId("INC-2025-01-15-cert-expiry")
                        .title("TLS Certificate Expiration Causing Service Outage")
                        .description("Inter-service mTLS certificate expired at midnight. All gRPC and HTTPS calls between services failed with certificate validation errors. Certificate rotation was manual and not automated. No alerting on certificate expiry.")
                        .resolution("Implemented cert-manager for automatic certificate rotation. Added certificate expiry monitoring (alert at 30, 14, 7 days). Switched to short-lived certificates (90 days). Added certificate validation in CI/CD pipeline.")
                        .servicePatterns(new String[]{"tls", "certificate", "mtls", "grpc", "security"})
                        .errorPatterns(new String[]{"certificate-expired", "ssl-handshake-failure", "cert-validation-error", "truststore"})
                        .build(),

                // Database deadlock
                IncidentEmbedding.builder()
                        .incidentId("INC-2024-10-22-db-deadlock")
                        .title("Database Deadlock in Order Processing")
                        .description("Deadlock detected in PostgreSQL during high-concurrency order placement. Two transactions updating orders and inventory in opposite order. Application didn't implement retry logic for deadlock errors (SQLSTATE 40P01).")
                        .resolution("Implemented consistent lock ordering (always lock orders before inventory). Added deadlock retry with exponential backoff (max 3 retries). Reduced transaction scope. Added deadlock monitoring alert.")
                        .servicePatterns(new String[]{"database", "deadlock", "postgresql", "order-processing", "concurrency"})
                        .errorPatterns(new String[]{"deadlock-detected", "40p01", "transaction-aborted", "lock-wait-timeout"})
                        .build(),

                // Config drift
                IncidentEmbedding.builder()
                        .incidentId("INC-2025-05-01-config-drift")
                        .title("Configuration Drift Between Environments")
                        .description("Production had different circuit breaker thresholds than staging. Staging: 50% failure rate, 10s timeout. Production: 90% failure rate, 60s timeout. Caused production to not trip circuit breaker when it should have, leading to prolonged degraded performance.")
                        .resolution("Implemented configuration as code (GitOps). Added configuration validation in CI/CD. Implemented configuration drift detection job. Standardized circuit breaker configs across environments.")
                        .servicePatterns(new String[]{"config-drift", "circuit-breaker", "gitops", "configuration"})
                        .errorPatterns(new String[]{"config-mismatch", "threshold-mismatch", "environment-drift", "validation-failure"})
                        .build(),

                // Third-party API rate limit
                IncidentEmbedding.builder()
                        .incidentId("INC-2024-09-18-rate-limit")
                        .title("Third-Party API Rate Limit Exceeded")
                        .description("Payment service hit Stripe API rate limits during flash sale. No client-side rate limiting or request queuing. Requests failed with 429, causing payment failures. Retry logic was immediate, exacerbating the problem.")
                        .resolution("Implemented token bucket rate limiter (100 req/s). Added request queue with max 1000 pending. Implemented exponential backoff on 429 (base 1s, max 60s). Added rate limit header parsing and proactive throttling.")
                        .servicePatterns(new String[]{"rate-limit", "third-party-api", "stripe", "payment", "token-bucket"})
                        .errorPatterns(new String[]{"rate-limit-exceeded", "429-too-many-requests", "throttling", "quota-exceeded"})
                        .build(),

                // Clock skew
                IncidentEmbedding.builder()
                        .incidentId("INC-2025-03-08-clock-skew")
                        .title("Clock Skew Causing JWT Validation Failures")
                        .description("Auth service and API gateway had 3-minute clock skew. JWT tokens issued by auth service were rejected by gateway as 'not yet valid' (nbf claim) or 'expired' (exp claim). NTP was not configured on gateway nodes.")
                        .resolution("Enabled NTP (chrony) on all nodes. Added clock skew tolerance in JWT validation (leeway 60s). Implemented clock synchronization monitoring alert. Added time sync check in health endpoint.")
                        .servicePatterns(new String[]{"clock-skew", "ntp", "jwt", "auth", "time-sync"})
                        .errorPatterns(new String[]{"token-not-yet-valid", "token-expired", "clock-skew", "nbf-claim", "exp-claim"})
                        .build(),

                // Feature flag rollout
                IncidentEmbedding.builder()
                        .incidentId("INC-2024-08-25-feature-flag")
                        .title("Feature Flag Rollout Causing Performance Regression")
                        .description("New recommendation algorithm enabled via feature flag for 100% users. Algorithm had O(n²) complexity for large catalogs. CPU usage spiked to 100%, causing 5s+ response times. No canary or gradual rollout.")
                        .resolution("Implemented progressive rollout (1% -> 5% -> 25% -> 100%). Added performance benchmark in CI for feature flags. Added automatic rollback on latency SLO breach. Implemented feature flag audit log.")
                        .servicePatterns(new String[]{"feature-flag", "rollout", "canary", "recommendation", "performance"})
                        .errorPatterns(new String[]{"performance-regression", "cpu-spike", "latency-slo-breach", "feature-flag-issue"})
                        .build()
        );
    }

    public float[] embedText(String text) {
        if (text == null || text.isBlank()) {
            return new float[1536];
        }
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.warn("Failed to embed text, returning zero vector: {}", e.getMessage());
            return new float[1536];
        }
    }

    public IncidentEmbedding saveIncident(IncidentEmbedding incident) {
        if (incident.getEmbedding() == null || incident.getEmbedding().length == 0) {
            incident.setEmbedding(embedText(incident.getDescription()));
        }
        incident.setUpdatedAt(java.time.Instant.now());
        return repository.save(incident);
    }

    public List<IncidentEmbedding> getAllIncidents() {
        return repository.findAll();
    }
}