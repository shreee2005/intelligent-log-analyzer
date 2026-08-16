package com.loganalyzer.analysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetrics {
    private String serviceId;
    private long totalLogs;
    private long errorCount;
    private long warningCount;
    private double errorRate;
    
    // Detailed counts by minute for charting
    private Map<String, Long> timeline;
}
