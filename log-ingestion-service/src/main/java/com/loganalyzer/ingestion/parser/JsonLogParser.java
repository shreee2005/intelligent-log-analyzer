package com.loganalyzer.ingestion.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.ingestion.model.LogEntry;
import com.loganalyzer.ingestion.model.LogFormat;
import com.loganalyzer.ingestion.model.LogLevel;
import com.loganalyzer.ingestion.model.LogRaw;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonLogParser implements LogParser {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(LogRaw rawLog) {
        return rawLog.getFormat() == LogFormat.JSON || 
               (rawLog.getFormat() == LogFormat.AUTO_DETECT && rawLog.getMessage().trim().startsWith("{"));
    }

    @Override
    public LogEntry parse(LogRaw rawLog) {
        try {
            JsonNode rootNode = objectMapper.readTree(rawLog.getMessage());
            
            // Extract fields, providing defaults if missing
            String msg = rootNode.has("message") ? rootNode.get("message").asText() : rawLog.getMessage();
            
            LogLevel level = LogLevel.INFO;
            if (rootNode.has("level")) {
                try {
                    level = LogLevel.valueOf(rootNode.get("level").asText().toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown log level in JSON: {}", rootNode.get("level").asText());
                }
            }

            Instant timestamp = Instant.now();
            if (rootNode.has("timestamp")) {
                try {
                    timestamp = Instant.parse(rootNode.get("timestamp").asText());
                } catch (Exception e) {
                    log.warn("Could not parse timestamp: {}", rootNode.get("timestamp").asText());
                }
            }

            return LogEntry.builder()
                    .id(UUID.randomUUID())
                    .timestamp(timestamp)
                    .ingestionTimestamp(Instant.now())
                    .serviceId(rawLog.getServiceId())
                    .level(level)
                    .message(msg)
                    .host(rawLog.getHost())
                    .rawLog(rawLog.getMessage())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse JSON log", e);
            // Fallback to treat it as plain text if JSON parsing fails entirely
            return LogEntry.builder()
                    .id(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .ingestionTimestamp(Instant.now())
                    .serviceId(rawLog.getServiceId())
                    .level(LogLevel.INFO)
                    .message(rawLog.getMessage())
                    .host(rawLog.getHost())
                    .rawLog(rawLog.getMessage())
                    .build();
        }
    }
}
