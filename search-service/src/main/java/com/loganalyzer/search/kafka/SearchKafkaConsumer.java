package com.loganalyzer.search.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.search.model.LogDocument;
import com.loganalyzer.search.repository.LogSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchKafkaConsumer {

    private final LogSearchRepository logSearchRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "logs.raw", groupId = "search-service")
    public void consumeLogForIndexing(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            
            Instant timestamp = Instant.now();
            if (root.hasNonNull("timestamp")) {
                try {
                    timestamp = Instant.parse(root.path("timestamp").asText());
                } catch (Exception ex) {
                    try {
                        double ts = root.path("timestamp").asDouble();
                        timestamp = Instant.ofEpochSecond((long) ts);
                    } catch (Exception ignore) {}
                }
            }

            Long projectId = null;
            if (root.hasNonNull("projectId")) {
                projectId = root.path("projectId").asLong();
            }

            String traceId = root.hasNonNull("traceId") ? root.path("traceId").asText() : null;
            String spanId = root.hasNonNull("spanId") ? root.path("spanId").asText() : null;

            LogDocument doc = LogDocument.builder()
                    .projectId(projectId)
                    .serviceId(root.path("serviceId").asText())
                    .level(root.path("level").asText())
                    .message(root.path("message").asText())
                    .timestamp(timestamp)
                    .traceId(traceId)
                    .spanId(spanId)
                    .build();

            logSearchRepository.save(doc);
            log.debug("Indexed log for search: {}", doc.getId());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse log for indexing: {}", message, e);
        }
    }
}
