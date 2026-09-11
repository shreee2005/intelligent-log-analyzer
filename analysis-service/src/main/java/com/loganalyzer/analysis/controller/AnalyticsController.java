package com.loganalyzer.analysis.controller;

import com.loganalyzer.analysis.model.DashboardMetrics;
import com.loganalyzer.analysis.repository.MetricsRedisRepository;
import com.loganalyzer.analysis.security.ProjectAccessClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import reactor.core.publisher.Mono;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final MetricsRedisRepository metricsRedisRepository;
    private final ProjectAccessClient projectAccessClient;

    @GetMapping("/metrics/{serviceId}")
    public Mono<ResponseEntity<?>> getMetrics(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Long projectId,
            @PathVariable String serviceId) {
        if (projectId == null || !projectAccessClient.hasAccess(projectId, authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Project access denied"));
        }
        return Mono.just(ResponseEntity.ok(
                metricsRedisRepository.getMetrics(projectId, serviceId)));
    }
    
    // Anomalies endpoint will be built by ML agent
}
