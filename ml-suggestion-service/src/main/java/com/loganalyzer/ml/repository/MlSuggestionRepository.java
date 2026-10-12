package com.loganalyzer.ml.repository;

import com.loganalyzer.ml.model.MlSuggestion;
import com.loganalyzer.ml.model.MlSuggestionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MlSuggestionRepository extends JpaRepository<MlSuggestion, Long> {

    Optional<MlSuggestion> findBySuggestionId(String suggestionId);

    Optional<MlSuggestion> findByAnomalyIdAndStatus(String anomalyId, String status);

    List<MlSuggestion> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<MlSuggestion> findByAnomalyId(String anomalyId);

    @Query(value = """
        INSERT INTO ml_suggestion_audit (
            suggestion_id, anomaly_id, project_id, service_id, model, model_version,
            latency_ms, prompt_tokens, completion_tokens, total_tokens, estimated_cost_usd,
            status, confidence, hypotheses_count, evidence_count, action_count, uncertainty_count,
            error_message, request_trace_id, request_priority, created_at
        ) VALUES (
            :suggestionId, :anomalyId, :projectId, :serviceId, :model, :modelVersion,
            :latencyMs, :promptTokens, :completionTokens, :totalTokens, :estimatedCostUsd,
            :status, :confidence, :hypothesesCount, :evidenceCount, :actionCount, :uncertaintyCount,
            :errorMessage, :requestTraceId, :requestPriority, :createdAt
        )
        """, nativeQuery = true)
    void insertAudit(@Param("suggestionId") String suggestionId,
                     @Param("anomalyId") String anomalyId,
                     @Param("projectId") Long projectId,
                     @Param("serviceId") String serviceId,
                     @Param("model") String model,
                     @Param("modelVersion") String modelVersion,
                     @Param("latencyMs") Long latencyMs,
                     @Param("promptTokens") Integer promptTokens,
                     @Param("completionTokens") Integer completionTokens,
                     @Param("totalTokens") Integer totalTokens,
                     @Param("estimatedCostUsd") Double estimatedCostUsd,
                     @Param("status") String status,
                     @Param("confidence") Double confidence,
                     @Param("hypothesesCount") Integer hypothesesCount,
                     @Param("evidenceCount") Integer evidenceCount,
                     @Param("actionCount") Integer actionCount,
                     @Param("uncertaintyCount") Integer uncertaintyCount,
                     @Param("errorMessage") String errorMessage,
                     @Param("requestTraceId") String requestTraceId,
                     @Param("requestPriority") String requestPriority,
                     @Param("createdAt") java.time.Instant createdAt);

    default void saveAudit(com.loganalyzer.ml.model.MlSuggestionAudit audit) {
        insertAudit(
                audit.getSuggestionId(), audit.getAnomalyId(), audit.getProjectId(),
                audit.getServiceId(), audit.getModel(), audit.getModelVersion(),
                audit.getLatencyMs(), audit.getPromptTokens(), audit.getCompletionTokens(),
                audit.getTotalTokens(), audit.getEstimatedCostUsd(), audit.getStatus(),
                audit.getConfidence(), audit.getHypothesesCount(), audit.getEvidenceCount(),
                audit.getActionCount(), audit.getUncertaintyCount(), audit.getErrorMessage(),
                audit.getRequestTraceId(), audit.getRequestPriority(), audit.getCreatedAt()
        );
    }
}