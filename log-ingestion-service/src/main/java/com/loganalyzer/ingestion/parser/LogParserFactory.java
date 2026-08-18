package com.loganalyzer.ingestion.parser;

import com.loganalyzer.ingestion.model.LogEntry;
import com.loganalyzer.ingestion.model.LogFormat;
import com.loganalyzer.ingestion.model.LogRaw;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class LogParserFactory {

    private final List<LogParser> parsers;

    public LogParserFactory(List<LogParser> parsers) {
        this.parsers = parsers;
    }

    public LogEntry parse(LogRaw rawLog) {
        for (LogParser parser : parsers) {
            if (parser.supports(rawLog)) {
                return parser.parse(rawLog);
            }
        }
        
        // Fallback if no specific parser supports it (should theoretically be handled by a plain text parser)
        log.warn("No specific parser found for format {}, falling back to basic parsing", rawLog.getFormat());
        return createBasicLogEntry(rawLog);
    }
    
    private LogEntry createBasicLogEntry(LogRaw rawLog) {
         return LogEntry.builder()
                .id(UUID.randomUUID())
                .timestamp(Instant.now())
                .ingestionTimestamp(Instant.now())
                .serviceId(rawLog.getServiceId())
                .message(rawLog.getMessage())
                .rawLog(rawLog.getMessage())
                .host(rawLog.getHost())
                .projectId(rawLog.getProjectId())
                .build();
    }
}
