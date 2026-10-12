package com.loganalyzer.ml.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "ml_suggestion_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlSuggestionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suggestion_id", nullable = false, length = 64)
    private String suggestionId;

    @Column(name = "anomaly_id", nullable = false, length = 128)
    private String anomalyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "service_id", nullable = false, length = 255)
    private String serviceId;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "estimated_cost_usd", precision = 10, scale = 6)
    private Double estimatedCostUsd;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "confidence", precision = 3, scale = 2)
    private Double confidence;

    @Column(name = "hypotheses_count")
    private Integer hypothesesCount;

    @Column(name = "evidence_count")
    private Integer evidenceCount;

    @Column(name = "action_count")
    private Integer actionCount;

    @Column(name = "uncertainty_count")
    private Integer uncertaintyCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "request_trace_id", length = 128)
    private String requestTraceId;

    @Column(name = "request_priority", length = 20)
    private String requestPriority;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}