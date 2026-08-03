package com.loganalyzer.ingestion.security;

import com.loganalyzer.ingestion.model.LogEntry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * Service responsible for removing Personally Identifiable Information (PII)
 * from log messages before they are processed or stored.
 */
@Service
public class PiiRedactionService {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern IP_PATTERN = Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b");
    // Simplified CC pattern for demonstration
    private static final Pattern CC_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b");
    
    public Mono<LogEntry> redact(LogEntry log) {
        return Mono.fromCallable(() -> {
            if (log.getMessage() != null) {
                String redactedMessage = redactSensitiveData(log.getMessage());
                log.setMessage(redactedMessage);
            }
            return log;
        });
    }
    
    private String redactSensitiveData(String message) {
        String redacted = EMAIL_PATTERN.matcher(message).replaceAll("[REDACTED_EMAIL]");
        redacted = IP_PATTERN.matcher(redacted).replaceAll("[REDACTED_IP]");
        redacted = CC_PATTERN.matcher(redacted).replaceAll("[REDACTED_CC]");
        return redacted;
    }
}
