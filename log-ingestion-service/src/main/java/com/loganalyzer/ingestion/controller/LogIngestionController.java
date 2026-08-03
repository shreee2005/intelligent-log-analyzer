package com.loganalyzer.ingestion.controller;

import com.loganalyzer.ingestion.model.LogIngestionResponse;
import com.loganalyzer.ingestion.model.LogRaw;
import com.loganalyzer.ingestion.service.LogIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogIngestionController {

    private final LogIngestionService logIngestionService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<LogIngestionResponse> ingestLog(@Valid @RequestBody LogRaw logRaw) {
        return logIngestionService.ingestLog(logRaw);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Flux<LogIngestionResponse> ingestLogBatch(@Valid @RequestBody List<LogRaw> logRaws) {
        // Flux.fromIterable creates a reactive stream from the list
        // flatMap processes them concurrently
        return Flux.fromIterable(logRaws)
                .flatMap(logIngestionService::ingestLog);
    }
}
