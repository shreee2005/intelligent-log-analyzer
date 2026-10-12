package com.loganalyzer.ml.service;

import com.loganalyzer.ml.client.AnalysisServiceClient;
import com.loganalyzer.ml.client.SearchServiceClient;
import com.loganalyzer.ml.dto.LogDocumentDto;
import com.loganalyzer.ml.dto.MetricsDto;
import com.loganalyzer.ml.dto.MlAnalysisRequest;
import com.loganalyzer.ml.dto.MlAnalysisResponse;
import com.loganalyzer.ml.dto.SuggestionResponse;
import com.loganalyzer.ml.model.MlSuggestion;
import com.loganalyzer.ml.repository.MlSuggestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final ContextBuilderService contextBuilderService;
    private final VectorSearchService vectorSearchService;
    private final SuggestionGenerationService generationService;
    private final MlSuggestionRepository suggestionRepository;
    private final SuggestionCacheService cacheService;
    private final SearchServiceClient searchServiceClient;
    private final AnalysisServiceClient analysisServiceClient;

    @org.springframework.beans.factory.annotation.Value("${ml.suggestion.rag.top-k:5}")
    private int ragTopK;

    @org.springframework.beans.factory.annotation.Value("${ml.suggestion.rag.similarity-threshold:0.7}")
    private double ragSimilarityThreshold;

    @org.springframework.beans.factory.annotation.Value("${ml.suggestion.context.max-logs:50}")
    private int maxLogs;

    public SuggestionResponse analyze(MlAnalysisRequest request, String jwtToken) {
        log.info("Starting analysis for anomalyId={}, projectId={}, serviceId={}", 
                request.getAnomalyId(), request.getProjectId(), request.getServiceId());

        try {
            // 1. Fetch context (logs, metrics, RAG)
            ContextBuilderService.AnalysisContext context = buildContext(request, jwtToken);

            // 2. Generate suggestion (with caching)
            MlSuggestion suggestion = generationService.generateSuggestion(
                    request, 
                    context.getRedactedPrompt(),
                    context.getAnomalyContext(),
                    context.getLogContext(),
                    context.getMetricContext(),
                    context.getRagContext()
            );

            // 3. Save to database
            MlSuggestion saved = suggestionRepository.save(suggestion);

            // 4. Convert to response DTO
            return toResponse(saved);

        } catch (Exception e) {
            log.error("Analysis failed for anomalyId={}: {}", request.getAnomalyId(), e.getMessage(), e);
            return SuggestionResponse.builder()
                    .suggestionId(java.util.UUID.randomUUID().toString())
                    .anomalyId(request.getAnomalyId())
                    .projectId(request.getProjectId())
                    .serviceId(request.getServiceId())
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private ContextBuilderService.AnalysisContext buildContext(MlAnalysisRequest request, String jwtToken) {
        // Fetch logs
        List<LogDocumentDto> logs;
        if (request.getTraceId() != null && !request.getTraceId().isBlank()) {
            logs = searchServiceClient.fetchLogsByProjectAndTrace(request.getProjectId(), request.getTraceId(), jwtToken);
            if (logs.size() < maxLogs) {
                // Supplement with recent logs from the service
                List<LogDocumentDto> recent = searchServiceClient
                        .fetchLogsByService(request.getProjectId(), request.getServiceId(), maxLogs - logs.size(), jwtToken);
                logs.addAll(recent);
            }
        } else {
            logs = searchServiceClient.fetchLogsByService(request.getProjectId(), request.getServiceId(), maxLogs, jwtToken);
        }

        // Fetch metrics
        MetricsDto metrics = analysisServiceClient.fetchMetricsForService(
                request.getProjectId(), request.getServiceId(), jwtToken);

        // Build RAG query from anomaly context
        String ragQuery = String.format("Service %s anomaly %s %s", 
                request.getServiceId(), request.getAnomalyId(), request.getPriority());

        // Search similar incidents
        List<VectorSearchService.RagResult> ragResults = vectorSearchService.searchSimilar(
                ragQuery, ragTopK, ragSimilarityThreshold);

        // Build context
        return contextBuilderService.buildContext(request, logs, metrics, ragResults);
    }

    public SuggestionResponse getSuggestion(String suggestionId) {
        return suggestionRepository.findBySuggestionId(suggestionId)
                .map(this::toResponse)
                .orElse(null);
    }

    public List<SuggestionResponse> getSuggestionsByAnomaly(String anomalyId) {
        return suggestionRepository.findByAnomalyId(anomalyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<SuggestionResponse> getSuggestionsByProject(Long projectId) {
        return suggestionRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private SuggestionResponse toResponse(MlSuggestion suggestion) {
        return SuggestionResponse.builder()
                .suggestionId(suggestion.getSuggestionId())
                .anomalyId(suggestion.getAnomalyId())
                .projectId(suggestion.getProjectId())
                .serviceId(suggestion.getServiceId())
                .traceId(suggestion.getTraceId())
                .model(suggestion.getModel())
                .modelVersion(suggestion.getModelVersion())
                .promptTokens(suggestion.getPromptTokens())
                .completionTokens(suggestion.getCompletionTokens())
                .totalTokens(suggestion.getTotalTokens())
                .estimatedCostUsd(suggestion.getEstimatedCostUsd())
                .latencyMs(suggestion.getLatencyMs())
                .hypotheses(suggestion.getHypotheses())
                .evidenceLogIds(suggestion.getEvidenceLogIds())
                .confidence(suggestion.getConfidence())
                .recommendedActions(suggestion.getRecommendedActions())
                .verificationSteps(suggestion.getVerificationSteps())
                .uncertainty(suggestion.getUncertainty())
                .ragSources(suggestion.getRagSources() != null ? suggestion.getRagSources().stream()
                        .map(r -> SuggestionResponse.RagSource.builder()
                                .incidentId(r.getIncidentId())
                                .title(r.getTitle())
                                .similarity(r.getSimilarity())
                                .build())
                        .toList() : List.of())
                .status(suggestion.getStatus())
                .errorMessage(suggestion.getErrorMessage())
                .createdAt(suggestion.getCreatedAt() != null ? suggestion.getCreatedAt().toString() : null)
                .updatedAt(suggestion.getUpdatedAt() != null ? suggestion.getUpdatedAt().toString() : null)
                .build();
    }
}