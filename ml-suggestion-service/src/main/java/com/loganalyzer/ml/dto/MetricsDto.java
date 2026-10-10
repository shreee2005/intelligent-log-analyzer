package com.loganalyzer.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsDto {

    @JsonProperty("serviceId")
    private String serviceId;

    @JsonProperty("totalLogs")
    private Long totalLogs;

    @JsonProperty("errorCount")
    private Long errorCount;

    @JsonProperty("warningCount")
    private Long warningCount;

    @JsonProperty("errorRate")
    private Double errorRate;

    @JsonProperty("timeline")
    private Map<String, Long> timeline;
}