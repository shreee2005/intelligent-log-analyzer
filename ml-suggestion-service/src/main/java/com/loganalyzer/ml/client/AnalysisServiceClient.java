package com.loganalyzer.ml.client;

import com.loganalyzer.ml.dto.MetricsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisServiceClient {

    private final WebClient webClient;

    public AnalysisServiceClient(@Value("${analysis.service.url:http://localhost:8083}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public MetricsDto fetchMetricsForService(Long projectId, String serviceId, String jwtToken) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/analytics/metrics/{serviceId}")
                            .queryParam("projectId", projectId)
                            .build(serviceId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToMono(MetricsDto.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch metrics for service {} project {}: {}", serviceId, projectId, e.getMessage());
                        return Mono.empty();
                    })
                    .block();
        } catch (Exception e) {
            log.error("Error fetching metrics for service {} project {}: {}", serviceId, projectId, e.getMessage());
            return null;
        }
    }

    public MetricsDto fetchMetricsForAllServices(Long projectId, String jwtToken) {
        try {
            // This would need a new endpoint in analysis-service
            // For now, we'll return null and let the caller fetch per-service
            log.debug("fetchMetricsForAllServices not implemented, caller should fetch per-service");
            return null;
        } catch (Exception e) {
            log.error("Error fetching metrics for all services project {}: {}", projectId, e.getMessage());
            return null;
        }
    }
}