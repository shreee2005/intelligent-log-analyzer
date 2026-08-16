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
    private String serviceId;
    private long errorCount;
    private double anomalyScore;
    private String severity; // e.g., "HIGH", "CRITICAL"
    private long windowStart;
    private long windowEnd;
}
