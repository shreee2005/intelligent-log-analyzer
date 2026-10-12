package com.loganalyzer.ml.kafka;

import com.loganalyzer.ml.dto.MlAnalysisRequest;
import com.loganalyzer.ml.service.SuggestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MlAnalysisConsumer {

    private final SuggestionService suggestionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${ml.suggestion.async.request-topic:ml.suggestions.request}", 
                   groupId = "ml-suggestion-service")
    public void consumeAnalysisRequest(String message) {
        try {
            log.info("Received analysis request from Kafka: {}", message);
            
            MlAnalysisRequest request = objectMapper.readValue(message, MlAnalysisRequest.class);
            
            // For async processing, we need a JWT token
            // In practice, this would come from a service account or be passed in the message
            // For now, we'll log and process synchronously
            log.info("Processing async analysis for anomalyId={}", request.getAnomalyId());
            
            // The actual async processing would require a service account token
            // This is a placeholder for the async flow
            
        } catch (Exception e) {
            log.error("Failed to process analysis request: {}", e.getMessage(), e);
            // Could send to DLQ topic here
        }
    }
}