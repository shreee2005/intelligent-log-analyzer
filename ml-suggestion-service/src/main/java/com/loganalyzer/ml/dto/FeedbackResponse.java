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
public class FeedbackResponse {

    @JsonProperty("feedbackId")
    private Long feedbackId;

    @JsonProperty("suggestionId")
    private String suggestionId;

    @JsonProperty("rating")
    private String rating;

    @JsonProperty("createdAt")
    private String createdAt;
}