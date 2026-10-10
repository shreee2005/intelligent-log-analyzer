package com.loganalyzer.ml.client;

import com.loganalyzer.ml.dto.LogDocumentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchServiceClient {

    private final WebClient webClient;

    public SearchServiceClient(@Value("${search.service.url:http://localhost:8084}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<LogDocumentDto> fetchLogsByProjectAndTrace(Long projectId, String traceId, String jwtToken) {
        if (traceId == null || traceId.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/search/trace")
                            .queryParam("projectId", projectId)
                            .queryParam("traceId", traceId)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToFlux(LogDocumentDto.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch logs by traceId {} for project {}: {}", traceId, projectId, e.getMessage());
                        return Mono.empty();
                    })
                    .collectList()
                    .block();
        } catch (Exception e) {
            log.error("Error fetching logs by traceId {} for project {}: {}", traceId, projectId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<LogDocumentDto> fetchRecentLogsByProject(Long projectId, int limit, String jwtToken) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/search")
                            .queryParam("projectId", projectId)
                            .queryParam("limit", limit)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToFlux(LogDocumentDto.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch recent logs for project {}: {}", projectId, e.getMessage());
                        return Mono.empty();
                    })
                    .collectList()
                    .block();
        } catch (Exception e) {
            log.error("Error fetching recent logs for project {}: {}", projectId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<LogDocumentDto> fetchLogsByService(Long projectId, String serviceId, int limit, String jwtToken) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/search/service")
                            .queryParam("projectId", projectId)
                            .queryParam("serviceId", serviceId)
                            .queryParam("limit", limit)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToFlux(LogDocumentDto.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch logs for service {} project {}: {}", serviceId, projectId, e.getMessage());
                        return Mono.empty();
                    })
                    .collectList()
                    .block();
        } catch (Exception e) {
            log.error("Error fetching logs for service {} project {}: {}", serviceId, projectId, e.getMessage());
            return Collections.emptyList();
        }
    }
}