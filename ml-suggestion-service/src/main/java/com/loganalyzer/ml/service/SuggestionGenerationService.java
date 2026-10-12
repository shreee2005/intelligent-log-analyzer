package com.loganalyzer.ml.service;

import com.loganalyzer.ml.dto.MlAnalysisRequest;
import com.loganalyzer.ml.model.MlSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionGenerationService {

    private final ChatClient chatClient;
    private final SuggestionCacheService cacheService;
    private final SuggestionAuditService auditService;

    @Value("${ml.suggestion.llm.provider:vertexai-gemini}")
    private String llmProvider;

    @Value("${ml.suggestion.llm.model:gemini-1.5-pro-001}")
    private String llmModel;

    @Value("${ml.suggestion.cache.ttl-seconds:3600}")
    private long cacheTtlSeconds;

    public MlSuggestion generateSuggestion(MlAnalysisRequest request, String redactedPrompt, 
                                           String anomalyContext, String logContext, String metricContext, String ragContext) {
        
        Instant startTime = Instant.now();
        String promptHash = computePromptHash(redactedPrompt);
        long latencyMs = 0;

        // Check cache first
        MlSuggestion cached = cacheService.get(request.getAnomalyId(), promptHash);
        if (cached != null) {
            log.info("Cache hit for anomalyId={}", request.getAnomalyId());
            return cached;
        }

        log.info("Generating suggestion for anomalyId={} using provider={}", request.getAnomalyId(), llmProvider);

        try {
            // Call LLM with structured output
            ChatResponse response = chatClient.prompt()
                    .user(redactedPrompt)
                    .call()
                    .chatResponse();

            String content = response.getResult().getOutput().getText();
            latencyMs = java.time.Duration.between(startTime, Instant.now()).toMillis();

            // Parse structured JSON response
            MlSuggestion suggestion = parseSuggestionResponse(content, request, response, latencyMs);
            
            // Cache the result
            cacheService.put(request.getAnomalyId(), promptHash, suggestion, cacheTtlSeconds);
            
            // Audit log (async)
            auditService.logSuggestion(suggestion, request, llmProvider, llmModel, latencyMs, 
                    getPromptTokens(response), getCompletionTokens(response));

            log.info("Generated suggestion for anomalyId={} in {}ms", request.getAnomalyId(), latencyMs);
            return suggestion;

        } catch (Exception e) {
            latencyMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            log.error("Failed to generate suggestion for anomalyId={}: {}", request.getAnomalyId(), e.getMessage());
            
            // Create failed suggestion record
            MlSuggestion failed = createFailedSuggestion(request, e.getMessage(), latencyMs);
            auditService.logSuggestion(failed, request, llmProvider, llmModel, latencyMs, 0, 0);
            return failed;
        }
    }

    private MlSuggestion parseSuggestionResponse(String content, MlAnalysisRequest request, 
                                                  ChatResponse response, long latencyMs) {
        try {
            // Use Jackson to parse the JSON response
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> parsed = mapper.readValue(content, Map.class);

            MlSuggestion suggestion = MlSuggestion.builder()
                    .suggestionId(UUID.randomUUID().toString())
                    .anomalyId(request.getAnomalyId())
                    .projectId(request.getProjectId())
                    .serviceId(request.getServiceId())
                    .traceId(request.getTraceId())
                    .model(llmModel)
                    .modelVersion(llmProvider)
                    .latencyMs(latencyMs)
                    .hypotheses(parseStringList(parsed.get("hypotheses")))
                    .evidenceLogIds(parseStringList(parsed.get("evidenceLogIds")))
                    .confidence(parseDouble(parsed.get("confidence")))
                    .recommendedActions(parseStringList(parsed.get("recommendedActions")))
                    .verificationSteps(parseStringList(parsed.get("verificationSteps")))
                    .uncertainty(parseStringList(parsed.get("uncertainty")))
                    .ragSources(parseRagSources(parsed.get("ragSources")))
                    .status("COMPLETED")
                    .build();

            // Calculate token usage and cost
            int promptTokens = getPromptTokens(response);
            int completionTokens = getCompletionTokens(response);
            suggestion.setPromptTokens(promptTokens);
            suggestion.setCompletionTokens(completionTokens);
            suggestion.setTotalTokens(promptTokens + completionTokens);
            suggestion.setEstimatedCostUsd(estimateCost(promptTokens, completionTokens));

            return suggestion;

        } catch (Exception e) {
            log.warn("Failed to parse LLM response, creating fallback: {}", e.getMessage());
            return createFailedSuggestion(request, "Failed to parse LLM response: " + e.getMessage(), latencyMs);
        }
    }

    private List<String> parseStringList(Object obj) {
        if (obj == null) return List.of();
        if (obj instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    private Double parseDouble(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private List<MlSuggestion.RagSource> parseRagSources(Object obj) {
        if (obj == null) return List.of();
        if (obj instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(map -> MlSuggestion.RagSource.builder()
                            .incidentId((String) map.get("incidentId"))
                            .title((String) map.get("title"))
                            .similarity(((Number) map.getOrDefault("similarity", 0)).doubleValue())
                            .build())
                    .toList();
        }
        return List.of();
    }

    private int getPromptTokens(ChatResponse response) {
        return response.getMetadata() != null && response.getMetadata().getUsage() != null 
                ? response.getMetadata().getUsage().getPromptTokens() : 0;
    }

    private int getCompletionTokens(ChatResponse response) {
        return response.getMetadata() != null && response.getMetadata().getUsage() != null 
                ? response.getMetadata().getUsage().getCompletionTokens() : 0;
    }

    private double estimateCost(int promptTokens, int completionTokens) {
        // Rough estimates for Gemini 1.5 Pro (prices per 1M tokens)
        double promptCostPerM = 1.25;  // $1.25 per 1M input tokens
        double completionCostPerM = 5.00; // $5.00 per 1M output tokens
        
        return (promptTokens * promptCostPerM + completionTokens * completionCostPerM) / 1_000_000.0;
    }

    private String computePromptHash(String prompt) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16); // First 16 chars for cache key
        } catch (Exception e) {
            return String.valueOf(prompt.hashCode());
        }
    }

    private MlSuggestion createFailedSuggestion(MlAnalysisRequest request, String error, long latencyMs) {
        return MlSuggestion.builder()
                .suggestionId(UUID.randomUUID().toString())
                .anomalyId(request.getAnomalyId())
                .projectId(request.getProjectId())
                .serviceId(request.getServiceId())
                .traceId(request.getTraceId())
                .model(llmModel)
                .modelVersion(llmProvider)
                .latencyMs(latencyMs)
                .hypotheses(List.of())
                .evidenceLogIds(List.of())
                .confidence(0.0)
                .recommendedActions(List.of())
                .verificationSteps(List.of())
                .uncertainty(List.of("Generation failed: " + error))
                .ragSources(List.of())
                .status("FAILED")
                .errorMessage(error)
                .build();
    }
}