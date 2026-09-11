package com.loganalyzer.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogAggregator {
    private Long projectId;
    private String serviceId;
    private long totalCount;
    private long errorCount;
    private long warningCount;
    private Instant windowStartTime;
    private Instant windowEndTime;

    public LogAggregator add(LogEntry log) {
        this.projectId = log.getProjectId();
        this.serviceId = log.getServiceId();
        this.totalCount++;
        
        if (log.getLevel() == LogLevel.ERROR || log.getLevel() == LogLevel.FATAL) {
            this.errorCount++;
        } else if (log.getLevel() == LogLevel.WARN) {
            this.warningCount++;
        }
        
        return this;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public double getErrorRate() {
        if (totalCount == 0) return 0.0;
        return (double) errorCount / totalCount;
    }
}
