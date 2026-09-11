package com.loganalyzer.processor.topology;

import com.loganalyzer.processor.model.Anomaly;
import com.loganalyzer.processor.model.LogAggregator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class AnomalyDetector {

    private static final double ERROR_RATE_THRESHOLD_HIGH = 0.10; // 10%
    private static final double ERROR_RATE_THRESHOLD_MEDIUM = 0.05; // 5%
    private static final long SPIKE_THRESHOLD = 1000; // 1000 logs in a window

    public Optional<Anomaly> detect(LogAggregator aggregator) {
        double errorRate = aggregator.getErrorRate();
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("errorRate", errorRate);
        metrics.put("totalCount", aggregator.getTotalCount());
        metrics.put("errorCount", aggregator.getErrorCount());

        if (errorRate > ERROR_RATE_THRESHOLD_HIGH) {
            return Optional.of(buildAnomaly(aggregator, 0.9, "CRITICAL: High error rate detected (>10%)", metrics));
        } else if (errorRate > ERROR_RATE_THRESHOLD_MEDIUM) {
            return Optional.of(buildAnomaly(aggregator, 0.6, "WARNING: Elevated error rate detected (>5%)", metrics));
        } else if (aggregator.getTotalCount() > SPIKE_THRESHOLD) {
            return Optional.of(buildAnomaly(aggregator, 0.5, "NOTICE: Unusual spike in log volume detected", metrics));
        }

        return Optional.empty();
    }

    private Anomaly buildAnomaly(LogAggregator aggregator, double severity, String description, Map<String, Object> metrics) {
        return Anomaly.builder()
                .id(UUID.randomUUID())
                .schemaVersion(1)
                .eventType("ANOMALY_DETECTED")
                .projectId(aggregator.getProjectId())
                .serviceId(aggregator.getServiceId())
                .detector("ERROR_RATE")
                .severity(severity >= 0.9 ? "CRITICAL" : severity >= 0.6 ? "HIGH" : "MEDIUM")
                .deduplicationKey(String.format("%s:%s:error-rate:%s",
                        aggregator.getProjectId(), aggregator.getServiceId(),
                        aggregator.getWindowEndTime()))
                .description(description)
                .timestamp(Instant.now())
                .metrics(metrics)
                .build();
    }
}
