package com.loganalyzer.ingestion.service;

import com.loganalyzer.ingestion.kafka.LogKafkaProducer;
import com.loganalyzer.ingestion.model.LogEntry;
import com.loganalyzer.ingestion.model.LogIngestionResponse;
import com.loganalyzer.ingestion.model.LogRaw;
import com.loganalyzer.ingestion.parser.LogParserFactory;
import com.loganalyzer.ingestion.security.PiiRedactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogIngestionService {

    private final LogParserFactory logParserFactory;
    private final PiiRedactionService piiRedactionService;
    private final LogKafkaProducer logKafkaProducer;

    public Mono<LogIngestionResponse> ingestLog(LogRaw rawLog) {
        return Mono.fromCallable(() -> logParserFactory.parse(rawLog))
                .flatMap(piiRedactionService::redact)
                .flatMap(processedLog -> {
                    logKafkaProducer.sendRawLog(processedLog);
                    return Mono.just(createSuccessResponse(processedLog));
                })
                .onErrorResume(e -> {
                    log.error("Error ingesting log", e);
                    // In a real system, we'd still want to track these failures, perhaps sending the raw string to an error topic
                    logKafkaProducer.sendErrorLog("unknown", "Failed processing log: " + e.getMessage() + " | Raw: " + rawLog.getMessage());
                    return Mono.just(createErrorResponse(e));
                });
    }

    private LogIngestionResponse createSuccessResponse(LogEntry processedLog) {
        return LogIngestionResponse.builder()
                .id(processedLog.getId())
                .status("ACCEPTED")
                .message("Log accepted for processing")
                .timestamp(Instant.now())
                .build();
    }

    private LogIngestionResponse createErrorResponse(Throwable e) {
        return LogIngestionResponse.builder()
                .status("ERROR")
                .message("Internal error during ingestion: " + e.getMessage())
                .timestamp(Instant.now())
                .build();
    }
}
