package com.loganalyzer.analysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolumeAnomaly {
    private int schemaVersion;
    private String eventType;
    private UUID eventId;
    private Long projectId;
    private String serviceId;
    private String detector;
    private String severity;
    private String deduplicationKey;
    private Instant timestamp;
    private double zScore;
    private long currentVolume;
    private double meanVolume;
    private double stdDev;
    private String type; // "SPIKE" or "DROP"
}
