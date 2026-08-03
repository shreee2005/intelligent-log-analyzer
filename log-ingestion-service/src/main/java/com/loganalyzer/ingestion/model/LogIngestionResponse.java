package com.loganalyzer.ingestion.model;

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
public class LogIngestionResponse {
    private UUID id;
    private String status;
    private String message;
    private Instant timestamp;
}
