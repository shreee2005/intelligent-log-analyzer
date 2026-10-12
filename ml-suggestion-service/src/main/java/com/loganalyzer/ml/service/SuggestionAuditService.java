package com.loganalyzer.ml.service;

import com.loganalyzer.ml.model.MlSuggestion;
import com.loganalyzer.ml.model.MlSuggestionAudit;
import com.loganalyzer.ml.repository.MlSuggestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionAuditService {

    private final MlSuggestionRepository suggestionRepository;

    @Async("auditExecutor")
    public void logSuggestion(MlSuggestion suggestion, com.loganalyzer.ml.dto.MlAnalysisRequest request,
                              String provider, String model, long latencyMs, int promptTokens, int completionTokens) {
        
        try {
            MlSuggestionAudit audit = MlSuggestionAudit.builder()
                    .suggestionId(suggestion.getSuggestionId())
                    .anomalyId(suggestion.getAnomalyId())
                    .projectId(suggestion.getProjectId())
                    .serviceId(suggestion.getServiceId())
                    .model(suggestion.getModel())
                    .modelVersion(suggestion.getModelVersion())
                    .latencyMs(suggestion.getLatencyMs())
                    .promptTokens(suggestion.getPromptTokens())
                    .completionTokens(suggestion.getCompletionTokens())
                    .totalTokens(suggestion.getTotalTokens())
                    .estimatedCostUsd(suggestion.getEstimatedCostUsd())
                    .status(suggestion.getStatus())
                    .confidence(suggestion.getConfidence())
                    .hypothesesCount(suggestion.getHypotheses() != null ? suggestion.getHypotheses().size() : 0)
                    .evidenceCount(suggestion.getEvidenceLogIds() != null ? suggestion.getEvidenceLogIds().size() : 0)
                    .actionCount(suggestion.getRecommendedActions() != null ? suggestion.getRecommendedActions().size() : 0)
                    .uncertaintyCount(suggestion.getUncertainty() != null ? suggestion.getUncertainty().size() : 0)
                    .errorMessage(suggestion.getErrorMessage())
                    .requestTraceId(request.getTraceId())
                    .requestPriority(request.getPriority())
                    .createdAt(Instant.now())
                    .build();

            suggestionRepository.saveAudit(audit);
            log.debug("Audit logged for suggestionId={}", suggestion.getSuggestionId());

        } catch (Exception e) {
            log.error("Failed to log audit for suggestion: {}", e.getMessage(), e);
        }
    }

    @Async("auditExecutor")
    public void logFeedback(String suggestionId, String userEmail, String rating, String comment) {
        try {
            // This would be implemented when feedback endpoint is added
            log.debug("Feedback logged for suggestionId={}, rating={}", suggestionId, rating);
        } catch (Exception e) {
            log.error("Failed to log feedback: {}", e.getMessage());
        }
    }
}