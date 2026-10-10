package com.loganalyzer.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlAnalysisRequest {

    @NotNull
    @JsonProperty("projectId")
    private Long projectId;

    @NotBlank
    @JsonProperty("anomalyId")
    private String anomalyId;

    @NotBlank
    @JsonProperty("serviceId")
    private String serviceId;

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("priority")
    private String priority; // HIGH, NORMAL, LOW
}