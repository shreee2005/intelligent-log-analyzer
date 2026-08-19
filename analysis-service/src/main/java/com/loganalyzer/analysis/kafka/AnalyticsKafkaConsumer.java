package com.loganalyzer.analysis.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.analysis.repository.MetricsRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsKafkaConsumer {

    private final MetricsRedisRepository metricsRedisRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "logs.raw", groupId = "analysis-service")
    public void consumeRawLog(String message) {
        try {
            // Because we deserialize as Map/JsonNode to avoid copying LogEntry.java again
            JsonNode root = objectMapper.readTree(message);
            
            String serviceId = root.path("serviceId").asText();
            String level = root.path("level").asText();
            Instant timestamp = Instant.now();
            
            if (root.hasNonNull("timestamp")) {
                try {
                    // Try parsing as double timestamp if it's numeric
                    double ts = root.path("timestamp").asDouble();
                    timestamp = Instant.ofEpochSecond((long) ts);
                } catch (Exception e) {
                    try {
                        timestamp = Instant.parse(root.path("timestamp").asText());
                    } catch (Exception ex) {
                        // ignore
                    }
                }
            }
            
            Long projectId = null;
            if (root.hasNonNull("projectId")) {
                projectId = root.path("projectId").asLong();
            }
            
            metricsRedisRepository.incrementLogCount(projectId, serviceId, level, timestamp);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse log message: {}", message, e);
        }
    }

    @KafkaListener(topics = "logs.anomalies", groupId = "analysis-service")
    public void consumeAnomaly(String message) {
        log.info("Received anomaly from Kafka Streams: {}", message);
        // Phase 3 ML logic will integrate here
    }
}
