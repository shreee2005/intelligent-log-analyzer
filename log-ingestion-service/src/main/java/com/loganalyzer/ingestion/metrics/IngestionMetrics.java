package com.loganalyzer.ingestion.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class IngestionMetrics {

    private final Counter logsIngestedCounter;
    private final Timer ingestionTimer;
    private final Counter logsErrorCounter;

    public IngestionMetrics(MeterRegistry registry) {
        this.logsIngestedCounter = Counter.builder("logs.ingested.total")
                .description("Total number of logs successfully ingested")
                .register(registry);

        this.ingestionTimer = Timer.builder("logs.ingestion.latency")
                .description("Time taken to parse and process a log before sending to Kafka")
                .register(registry);

        this.logsErrorCounter = Counter.builder("logs.ingestion.errors")
                .description("Total number of logs that failed during ingestion")
                .register(registry);
    }

    public void incrementIngested() {
        logsIngestedCounter.increment();
    }

    public void incrementError() {
        logsErrorCounter.increment();
    }

    public Timer getIngestionTimer() {
        return ingestionTimer;
    }
}
