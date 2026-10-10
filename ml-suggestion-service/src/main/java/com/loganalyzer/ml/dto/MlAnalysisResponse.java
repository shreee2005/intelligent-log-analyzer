package com.loganalyzer.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlAnalysisResponse {

    @JsonProperty("suggestionId")
    private String suggestionId;

    @JsonProperty("status")
    private String status; // QUEUED, PROCESSING, COMPLETED, FAILED

    @JsonProperty("message")
    private String message;
}