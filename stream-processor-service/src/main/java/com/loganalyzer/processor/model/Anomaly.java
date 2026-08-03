package com.loganalyzer.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Anomaly {
    private UUID id;
    private String serviceId;
    private double severity; // 0.0 to 1.0
    private String description;
    private Instant timestamp;
    private Map<String, Object> metrics;
}
