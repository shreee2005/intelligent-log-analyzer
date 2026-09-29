package com.loganalyzer.alert.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.alert.model.AnomalyEvent;
import com.loganalyzer.alert.service.NotificationService;
import com.loganalyzer.alert.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertKafkaConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;

    @KafkaListener(topics = "logs.anomalies", groupId = "alert-service-group")
    public void consumeAnomaly(String message) {
        try {
            log.info("Received anomaly event from Kafka: {}", message);
            
            JsonNode eventNode = objectMapper.readTree(message);
            
            // Support both VolumeAnomaly (from analysis-service) and AnomalyEvent formats
            String severity = eventNode.has("severity") ? eventNode.get("severity").asText() : "UNKNOWN";
            String serviceId = eventNode.has("serviceId") ? eventNode.get("serviceId").asText() : "unknown";
            
            // We only send alerts for HIGH or CRITICAL severities
            if ("HIGH".equalsIgnoreCase(severity) || "CRITICAL".equalsIgnoreCase(severity)) {
                // Convert to AnomalyEvent for notification service
                AnomalyEvent event = convertToAnomalyEvent(eventNode);
                notificationService.sendAnomalyAlert(event);
                webhookService.enqueue(event);
            } else {
                log.debug("Ignoring LOW/MEDIUM severity anomaly for service {}", serviceId);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse anomaly event: {}", message, e);
        }
    }
    
    private AnomalyEvent convertToAnomalyEvent(JsonNode node) {
        AnomalyEvent.AnomalyEventBuilder builder = AnomalyEvent.builder();
        
        if (node.has("schemaVersion")) builder.schemaVersion(node.get("schemaVersion").asInt());
        if (node.has("eventType")) builder.eventType(node.get("eventType").asText());
        if (node.has("eventId")) builder.eventId(node.get("eventId").asText());
        if (node.has("projectId")) builder.projectId(node.get("projectId").asLong());
        if (node.has("serviceId")) builder.serviceId(node.get("serviceId").asText());
        if (node.has("detector")) builder.detector(node.get("detector").asText());
        if (node.has("severity")) builder.severity(node.get("severity").asText());
        if (node.has("deduplicationKey")) builder.deduplicationKey(node.get("deduplicationKey").asText());
        if (node.has("timestamp")) {
            try {
                // Handle both epoch seconds (double) and ISO string formats
                JsonNode tsNode = node.get("timestamp");
                if (tsNode.isNumber()) {
                    builder.timestamp(java.time.Instant.ofEpochSecond(tsNode.asLong()));
                } else {
                    builder.timestamp(java.time.Instant.parse(tsNode.asText()));
                }
            } catch (Exception e) {
                log.warn("Failed to parse timestamp, using current time", e);
                builder.timestamp(java.time.Instant.now());
            }
        }
        
        // Add metrics from VolumeAnomaly fields if present
        java.util.Map<String, Object> metrics = new java.util.HashMap<>();
        if (node.has("zScore")) metrics.put("zScore", node.get("zScore").asDouble());
        if (node.has("currentVolume")) metrics.put("currentVolume", node.get("currentVolume").asLong());
        if (node.has("meanVolume")) metrics.put("meanVolume", node.get("meanVolume").asDouble());
        if (node.has("stdDev")) metrics.put("stdDev", node.get("stdDev").asDouble());
        if (node.has("type")) metrics.put("type", node.get("type").asText());
        if (!metrics.isEmpty()) builder.metrics(metrics);
        
        // Description based on type
        if (node.has("type")) {
            String type = node.get("type").asText();
            double zScore = node.has("zScore") ? node.get("zScore").asDouble() : 0;
            builder.description(String.format("Volume %s detected for service %s (Z-Score: %.2f)", 
                    type, node.get("serviceId").asText(), zScore));
        }
        
        return builder.build();
    }
}
