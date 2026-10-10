package com.loganalyzer.ml.service;

import com.loganalyzer.ml.dto.LogDocumentDto;
import com.loganalyzer.ml.dto.MetricsDto;
import com.loganalyzer.ml.dto.MlAnalysisRequest;
import com.loganalyzer.ml.model.IncidentEmbedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextBuilderService {

    private final PiiRedactionService piiRedactionService;
    private final VectorSearchService vectorSearchService;

    public AnalysisContext buildContext(MlAnalysisRequest request, 
                                        List<LogDocumentDto> logs,
                                        MetricsDto metrics,
                                        List<VectorSearchService.RagResult> ragResults) {
        
        String logContext = buildLogContext(logs);
        String metricContext = buildMetricContext(metrics);
        String ragContext = buildRagContext(ragResults);
        String anomalyContext = buildAnomalyContext(request);

        String fullPrompt = buildPrompt(anomalyContext, logContext, metricContext, ragContext);
        String redactedPrompt = piiRedactionService.redact(fullPrompt);

        return AnalysisContext.builder()
                .projectId(request.getProjectId())
                .anomalyId(request.getAnomalyId())
                .serviceId(request.getServiceId())
                .traceId(request.getTraceId())
                .logContext(logContext)
                .metricContext(metricContext)
                .ragContext(ragContext)
                .anomalyContext(anomalyContext)
                .fullPrompt(fullPrompt)
                .redactedPrompt(redactedPrompt)
                .logCount(logs != null ? logs.size() : 0)
                .ragResultCount(ragResults != null ? ragResults.size() : 0)
                .hasMetrics(metrics != null)
                .build();
    }

    private String buildLogContext(List<LogDocumentDto> logs) {
        if (logs == null || logs.isEmpty()) {
            return "No logs available for this anomaly.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== RECENT LOGS (").append(logs.size()).append(" entries) ===\n");

        for (LogDocumentDto log : logs) {
            String timestamp = log.getTimestamp() != null ? log.getTimestamp() : "unknown";
            String level = log.getLevel() != null ? log.getLevel() : "INFO";
            String service = log.getServiceId() != null ? log.getServiceId() : "unknown";
            String message = log.getMessage() != null ? log.getMessage() : "";
            String traceId = log.getTraceId() != null ? log.getTraceId() : "";

            sb.append(String.format("[%s] [%s] [%s] %s%s\n", 
                    timestamp, level, service, message,
                    traceId.isEmpty() ? "" : " (trace: " + traceId + ")"));
        }

        return sb.toString();
    }

    private String buildMetricContext(MetricsDto metrics) {
        if (metrics == null) {
            return "No metrics available.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== METRICS (24h) ===\n");
        sb.append(String.format("Service: %s\n", metrics.getServiceId()));
        sb.append(String.format("Total Logs: %d\n", metrics.getTotalLogs()));
        sb.append(String.format("Error Count: %d\n", metrics.getErrorCount()));
        sb.append(String.format("Warning Count: %d\n", metrics.getWarningCount()));
        sb.append(String.format("Error Rate: %.4f%%\n", metrics.getErrorRate() * 100));

        if (metrics.getTimeline() != null && !metrics.getTimeline().isEmpty()) {
            sb.append("Error Timeline (last 60 minutes):\n");
            metrics.getTimeline().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .limit(60)
                    .forEach(entry -> sb.append(String.format("  %s: %d errors\n", entry.getKey(), entry.getValue())));
        }

        return sb.toString();
    }

    private String buildRagContext(List<VectorSearchService.RagResult> ragResults) {
        if (ragResults == null || ragResults.isEmpty()) {
            return "No relevant historical incidents found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== RELEVANT HISTORICAL INCIDENTS ===\n");

        for (int i = 0; i < ragResults.size(); i++) {
            var result = ragResults.get(i);
            var incident = result.incident();
            
            sb.append(String.format("\n--- Incident %d (Similarity: %.2f%%) ---\n", i + 1, result.similarity() * 100));
            sb.append(String.format("ID: %s\n", incident.getIncidentId()));
            sb.append(String.format("Title: %s\n", incident.getTitle()));
            sb.append(String.format("Description: %s\n", incident.getDescription()));
            if (incident.getResolution() != null) {
                sb.append(String.format("Resolution: %s\n", incident.getResolution()));
            }
            if (incident.getServicePatterns() != null && incident.getServicePatterns().length > 0) {
                sb.append(String.format("Service Patterns: %s\n", String.join(", ", incident.getServicePatterns())));
            }
            if (incident.getErrorPatterns() != null && incident.getErrorPatterns().length > 0) {
                sb.append(String.format("Error Patterns: %s\n", String.join(", ", incident.getErrorPatterns())));
            }
        }

        return sb.toString();
    }

    private String buildAnomalyContext(MlAnalysisRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ANOMALY DETAILS ===\n");
        sb.append(String.format("Anomaly ID: %s\n", request.getAnomalyId()));
        sb.append(String.format("Project ID: %d\n", request.getProjectId()));
        sb.append(String.format("Service ID: %s\n", request.getServiceId()));
        if (request.getTraceId() != null) {
            sb.append(String.format("Trace ID: %s\n", request.getTraceId()));
        }
        if (request.getPriority() != null) {
            sb.append(String.format("Priority: %s\n", request.getPriority()));
        }
        return sb.toString();
    }

    private String buildPrompt(String anomalyContext, String logContext, String metricContext, String ragContext) {
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        return String.format("""
                # ANOMALY ANALYSIS REQUEST
                Generated: %s
                
                %s
                
                %s
                
                %s
                
                %s
                
                # TASK
                Analyze the anomaly above using the provided logs, metrics, and historical incidents.
                Generate a structured analysis following the exact JSON schema provided below.
                
                # OUTPUT SCHEMA (strict JSON)
                {{
                  "hypotheses": ["string", ...],           // 2-5 root cause hypotheses, ordered by likelihood
                  "evidenceLogIds": ["string", ...],       // Log IDs from context that support hypotheses
                  "confidence": 0.0-1.0,                   // Overall confidence in analysis
                  "recommendedActions": ["string", ...],   // 3-7 concrete, actionable remediation steps
                  "verificationSteps": ["string", ...],    // 3-5 specific commands/queries to verify fix
                  "uncertainty": ["string", ...]           // 1-3 areas where analysis is uncertain
                }}
                
                # GUIDELINES
                - Be specific: reference exact error messages, service names, metrics
                - Evidence must come from provided logs/metrics only
                - Actions must be executable (commands, config changes, queries)
                - Confidence should reflect evidence strength
                - Acknowledge uncertainty honestly
                """, timestamp, anomalyContext, logContext, metricContext, ragContext);
    }

    @lombok.Builder
    @lombok.Data
    public static class AnalysisContext {
        private Long projectId;
        private String anomalyId;
        private String serviceId;
        private String traceId;
        private String logContext;
        private String metricContext;
        private String ragContext;
        private String anomalyContext;
        private String fullPrompt;
        private String redactedPrompt;
        private int logCount;
        private int ragResultCount;
        private boolean hasMetrics;
    }
}