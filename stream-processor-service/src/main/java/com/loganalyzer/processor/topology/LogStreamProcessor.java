package com.loganalyzer.processor.topology;

import com.loganalyzer.processor.config.TopicsConfig;
import com.loganalyzer.processor.model.Anomaly;
import com.loganalyzer.processor.model.LogAggregator;
import com.loganalyzer.processor.model.LogEntry;
import com.loganalyzer.processor.serde.JsonSerdeFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogStreamProcessor {

    private final AnomalyDetector anomalyDetector;

    @Autowired
    public void buildPipeline(StreamsBuilder streamsBuilder) {
        
        KStream<String, LogEntry> logStream = streamsBuilder.stream(
                TopicsConfig.RAW_LOGS_TOPIC,
                Consumed.with(Serdes.String(), JsonSerdeFactory.createSerde(LogEntry.class))
        );

        // Group by serviceId, window by 1 minute, and aggregate
        KTable<Windowed<String>, LogAggregator> aggregatedLogs = logStream
                .groupByKey(Grouped.with(Serdes.String(), JsonSerdeFactory.createSerde(LogEntry.class)))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .aggregate(
                        LogAggregator::new, // Initializer
                        (key, logEntry, aggregate) -> aggregate.add(logEntry), // Aggregator
                        Materialized.with(Serdes.String(), JsonSerdeFactory.createSerde(LogAggregator.class))
                );

        // Detect anomalies and push to anomalies topic
        aggregatedLogs
                .toStream()
                .mapValues(anomalyDetector::detect)
                .filter((key, anomalyOptional) -> anomalyOptional.isPresent())
                .mapValues(java.util.Optional::get)
                .peek((key, anomaly) -> log.warn("Anomaly detected for service {}: {}", anomaly.getServiceId(), anomaly.getDescription()))
                // Re-key by serviceId for the output topic (dropping the window info from the key)
                .<String>selectKey((windowedKey, anomaly) -> anomaly.getServiceId())
                .to(
                        TopicsConfig.ANOMALIES_TOPIC,
                        Produced.with(Serdes.String(), JsonSerdeFactory.createSerde(Anomaly.class))
                );
    }
}
