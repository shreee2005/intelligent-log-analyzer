package com.loganalyzer.ingestion.controller;

import com.loganalyzer.ingestion.model.LogIngestionResponse;
import com.loganalyzer.ingestion.model.LogRaw;
import com.loganalyzer.ingestion.service.LogIngestionService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/logs")
public class LogIngestionController {

    private final LogIngestionService logIngestionService;
    private final WebClient webClient;

    public LogIngestionController(LogIngestionService logIngestionService,
                                  @Value("${auth.service.url:http://auth-service:8091}") String authServiceUrl) {
        this.logIngestionService = logIngestionService;
        this.webClient = WebClient.builder().baseUrl(authServiceUrl).build();
    }

    private Mono<Long> validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Mono.empty();
        }
        return webClient.get()
                .uri("/api/projects/key/{apiKey}", apiKey)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> ((Number) response.get("projectId")).longValue())
                .onErrorResume(e -> {
                    log.error("API Key validation failed for key: {}", apiKey, e);
                    return Mono.empty();
                });
    }

    @PostMapping
    public Mono<ResponseEntity<LogIngestionResponse>> ingestLog(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey,
            @Valid @RequestBody LogRaw logRaw) {
        
        return validateApiKey(apiKey)
                .flatMap(projectId -> {
                    logRaw.setProjectId(projectId);
                    return logIngestionService.ingestLog(logRaw)
                            .map(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    LogIngestionResponse errorResponse = LogIngestionResponse.builder()
                            .status("ERROR")
                            .message("Invalid or missing API Key")
                            .timestamp(Instant.now())
                            .build();
                    ResponseEntity<LogIngestionResponse> unauthorized = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
                    return Mono.just(unauthorized);
                }));
    }

    @PostMapping("/batch")
    public Mono<ResponseEntity<Flux<LogIngestionResponse>>> ingestLogBatch(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey,
            @Valid @RequestBody List<LogRaw> logRaws) {
        
        return validateApiKey(apiKey)
                .map(projectId -> {
                    logRaws.forEach(log -> log.setProjectId(projectId));
                    Flux<LogIngestionResponse> responseFlux = Flux.fromIterable(logRaws)
                            .flatMap(logIngestionService::ingestLog);
                    ResponseEntity<Flux<LogIngestionResponse>> accepted = ResponseEntity.status(HttpStatus.ACCEPTED).body(responseFlux);
                    return accepted;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    ResponseEntity<Flux<LogIngestionResponse>> unauthorized = ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                    return Mono.just(unauthorized);
                }));
    }
}
