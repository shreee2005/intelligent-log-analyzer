package com.loganalyzer.analysis.controller;

import com.loganalyzer.analysis.model.DashboardMetrics;
import com.loganalyzer.analysis.repository.MetricsRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import reactor.core.publisher.Mono;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final MetricsRedisRepository metricsRedisRepository;

    @GetMapping("/metrics/{serviceId}")
    public Mono<DashboardMetrics> getMetrics(@PathVariable String serviceId) {
        return Mono.just(metricsRedisRepository.getMetrics(serviceId));
    }
    
    // Anomalies endpoint will be built by ML agent
}
