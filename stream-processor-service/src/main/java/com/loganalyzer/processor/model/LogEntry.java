package com.loganalyzer.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {
    private UUID id;
    private Instant timestamp;
    private String serviceId;
    private LogLevel level;
    private String message;
    private String host;
    private Map<String, Object> metadata;
    private List<String> tags;
    private String rawLog;
    private Instant ingestionTimestamp;
    private Long projectId;
}
