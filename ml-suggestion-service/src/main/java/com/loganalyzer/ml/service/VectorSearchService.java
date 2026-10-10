package com.loganalyzer.ml.service;

import com.loganalyzer.ml.model.IncidentEmbedding;
import com.loganalyzer.ml.repository.IncidentEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final IncidentEmbeddingRepository repository;
    private final IncidentEmbeddingService embeddingService;

    public List<RagResult> searchSimilar(String queryText, int topK, double similarityThreshold) {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }

        try {
            float[] queryEmbedding = embeddingService.embedText(queryText);
            
            if (isZeroVector(queryEmbedding)) {
                log.warn("Query embedding is zero vector, falling back to keyword search");
                return keywordSearch(queryText, topK);
            }

            List<IncidentEmbedding> results = repository.findTopKSimilar(queryEmbedding, topK * 2);
            
            List<RagResult> ragResults = results.stream()
                    .map(incident -> {
                        double similarity = cosineSimilarity(queryEmbedding, incident.getEmbedding());
                        return new RagResult(incident, similarity);
                    })
                    .filter(r -> r.similarity() >= similarityThreshold)
                    .limit(topK)
                    .collect(Collectors.toList());

            log.debug("Vector search returned {} results for query: {}", ragResults.size(), 
                    queryText.substring(0, Math.min(100, queryText.length())));
            return ragResults;

        } catch (Exception e) {
            log.warn("Vector search failed, falling back to keyword search: {}", e.getMessage());
            return keywordSearch(queryText, topK);
        }
    }

    public List<RagResult> searchByServicePattern(String queryText, String servicePattern, int topK, double similarityThreshold) {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }

        try {
            float[] queryEmbedding = embeddingService.embedText(queryText);
            
            if (isZeroVector(queryEmbedding)) {
                return keywordSearchByService(queryText, servicePattern, topK);
            }

            List<IncidentEmbedding> results = repository.findSimilarByServicePattern(
                    queryEmbedding, servicePattern, topK * 2);

            return results.stream()
                    .map(incident -> {
                        double similarity = cosineSimilarity(queryEmbedding, incident.getEmbedding());
                        return new RagResult(incident, similarity);
                    })
                    .filter(r -> r.similarity() >= similarityThreshold)
                    .limit(topK)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Service pattern search failed: {}", e.getMessage());
            return keywordSearchByService(queryText, servicePattern, topK);
        }
    }

    private List<RagResult> keywordSearch(String queryText, int topK) {
        String[] keywords = extractKeywords(queryText);
        List<IncidentEmbedding> all = repository.findAll();
        
        return all.stream()
                .map(incident -> {
                    double score = keywordMatchScore(keywords, incident);
                    return new RagResult(incident, score);
                })
                .filter(r -> r.similarity() > 0)
                .sorted((a, b) -> Double.compare(b.similarity(), a.similarity()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    private List<RagResult> keywordSearchByService(String queryText, String servicePattern, int topK) {
        String[] keywords = extractKeywords(queryText);
        List<IncidentEmbedding> all = repository.findAll();
        
        return all.stream()
                .filter(incident -> incident.getServicePatterns() != null &&
                        java.util.Arrays.asList(incident.getServicePatterns()).contains(servicePattern))
                .map(incident -> {
                    double score = keywordMatchScore(keywords, incident);
                    return new RagResult(incident, score);
                })
                .filter(r -> r.similarity() > 0)
                .sorted((a, b) -> Double.compare(b.similarity(), a.similarity()))
                .limit(topK)
                .collect(Collectors.toList());
    }

    private String[] extractKeywords(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", " ")
                .split("\\s+");
    }

    private double keywordMatchScore(String[] keywords, IncidentEmbedding incident) {
        String searchableText = ((incident.getTitle() != null ? incident.getTitle() : "") + " " +
                (incident.getDescription() != null ? incident.getDescription() : "") + " " +
                (incident.getResolution() != null ? incident.getResolution() : "") + " " +
                String.join(" ", incident.getServicePatterns() != null ? incident.getServicePatterns() : new String[0]) + " " +
                String.join(" ", incident.getErrorPatterns() != null ? incident.getErrorPatterns() : new String[0]))
                .toLowerCase();

        int matches = 0;
        for (String keyword : keywords) {
            if (keyword.length() > 2 && searchableText.contains(keyword)) {
                matches++;
            }
        }
        return keywords.length > 0 ? (double) matches / keywords.length : 0.0;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private boolean isZeroVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            return true;
        }
        for (float v : vector) {
            if (v != 0.0f) {
                return false;
            }
        }
        return true;
    }

    public record RagResult(
            IncidentEmbedding incident,
            double similarity
    ) {
        public String getIncidentId() {
            return incident.getIncidentId();
        }

        public String getTitle() {
            return incident.getTitle();
        }

        public String getDescription() {
            return incident.getDescription();
        }

        public String getResolution() {
            return incident.getResolution();
        }

        public String[] getServicePatterns() {
            return incident.getServicePatterns();
        }

        public String[] getErrorPatterns() {
            return incident.getErrorPatterns();
        }
    }
}