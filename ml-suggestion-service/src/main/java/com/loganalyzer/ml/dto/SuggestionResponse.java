package com.loganalyzer.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionResponse {

    @JsonProperty("suggestionId")
    private String suggestionId;

    @JsonProperty("anomalyId")
    private String anomalyId;

    @JsonProperty("projectId")
    private Long projectId;

    @JsonProperty("serviceId")
    private String serviceId;

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("model")
    private String model;

    @JsonProperty("modelVersion")
    private String modelVersion;

    @JsonProperty("promptTokens")
    private Integer promptTokens;

    @JsonProperty("completionTokens")
    private Integer completionTokens;

    @JsonProperty("totalTokens")
    private Integer totalTokens;

    @JsonProperty("estimatedCostUsd")
    private Double estimatedCostUsd;

    @JsonProperty("latencyMs")
    private Long latencyMs;

    @JsonProperty("hypotheses")
    private List<String> hypotheses;

    @JsonProperty("evidenceLogIds")
    private List<String> evidenceLogIds;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("recommendedActions")
    private List<String> recommendedActions;

    @JsonProperty("verificationSteps")
    private List<String> verificationSteps;

    @JsonProperty("uncertainty")
    private List<String> uncertainty;

    @JsonProperty("ragSources")
    private List<RagSource> ragSources;

    @JsonProperty("status")
    private String status;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagSource {
        @JsonProperty("incidentId")
        private String incidentId;

        @JsonProperty("title")
        private String title;

        @JsonProperty("similarity")
        private Double similarity;
    }
}