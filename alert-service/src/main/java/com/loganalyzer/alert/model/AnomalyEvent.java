package com.loganalyzer.alert.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyEvent {
    private int schemaVersion;
    private String eventType;
    private String eventId;
    private Long projectId;
    private String serviceId;
    private String detector;
    private String severity;
    private String deduplicationKey;
    private String description;
    private java.time.Instant timestamp;
    private java.util.Map<String, Object> metrics;
}
