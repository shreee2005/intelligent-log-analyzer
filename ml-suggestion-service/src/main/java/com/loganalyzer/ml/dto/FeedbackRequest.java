package com.loganalyzer.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {

    @NotBlank
    @JsonProperty("rating")
    private String rating; // ACCEPTED, REJECTED, EDITED

    @JsonProperty("correctedAction")
    private String correctedAction;

    @JsonProperty("comment")
    private String comment;
}