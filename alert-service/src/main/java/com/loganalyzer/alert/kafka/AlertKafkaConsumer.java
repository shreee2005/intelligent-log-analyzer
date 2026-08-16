package com.loganalyzer.alert.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.alert.model.AnomalyEvent;
import com.loganalyzer.alert.service.NotificationService;
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

    @KafkaListener(topics = "logs.anomalies", groupId = "alert-service-group")
    public void consumeAnomaly(String message) {
        try {
            log.info("Received anomaly event from Kafka: {}", message);
            
            AnomalyEvent event = objectMapper.readValue(message, AnomalyEvent.class);
            
            // We only send alerts for HIGH or CRITICAL severities
            if ("HIGH".equalsIgnoreCase(event.getSeverity()) || "CRITICAL".equalsIgnoreCase(event.getSeverity())) {
                notificationService.sendAnomalyAlert(event);
            } else {
                log.debug("Ignoring LOW/MEDIUM severity anomaly for service {}", event.getServiceId());
            }
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse anomaly event: {}", message, e);
        }
    }
}
