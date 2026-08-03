package com.loganalyzer.ingestion.kafka;

import com.loganalyzer.ingestion.model.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendRawLog(LogEntry logEntry) {
        kafkaTemplate.send(KafkaTopicConfig.RAW_LOGS_TOPIC, logEntry.getServiceId(), logEntry)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Sent log=[{}] with offset=[{}]", logEntry.getId(), result.getRecordMetadata().offset());
                    } else {
                        log.error("Unable to send log=[{}] due to : {}", logEntry.getId(), ex.getMessage());
                        // Send to dead letter queue or error topic
                        sendErrorLog(logEntry.getId().toString(), "Failed to send to raw topic: " + ex.getMessage());
                    }
                });
    }

    public void sendErrorLog(String logId, String errorMessage) {
        kafkaTemplate.send(KafkaTopicConfig.ERROR_LOGS_TOPIC, logId, errorMessage);
    }
}
