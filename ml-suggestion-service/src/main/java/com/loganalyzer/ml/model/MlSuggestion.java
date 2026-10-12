package com.loganalyzer.ml.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "ml_suggestions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suggestion_id", nullable = false, unique = true, length = 64)
    private String suggestionId;

    @Column(name = "anomaly_id", nullable = false, length = 128)
    private String anomalyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "service_id", nullable = false, length = 255)
    private String serviceId;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "estimated_cost_usd", precision = 10, scale = 6)
    private Double estimatedCostUsd;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "hypotheses", columnDefinition = "JSONB")
    private List<String> hypotheses;

    @Column(name = "evidence_log_ids", columnDefinition = "JSONB")
    private List<String> evidenceLogIds;

    @Column(name = "confidence", precision = 3, scale = 2)
    private Double confidence;

    @Column(name = "recommended_actions", columnDefinition = "JSONB")
    private List<String> recommendedActions;

    @Column(name = "verification_steps", columnDefinition = "JSONB")
    private List<String> verificationSteps;

    @Column(name = "uncertainty", columnDefinition = "JSONB")
    private List<String> uncertainty;

    @Column(name = "rag_sources", columnDefinition = "JSONB")
    private List<RagSource> ragSources;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagSource {
        private String incidentId;
        private String title;
        private Double similarity;
    }
}