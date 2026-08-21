package com.loganalyzer.ingestion.model;

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
public class LogRaw {
    @NotBlank(message = "Log message cannot be blank")
    private String message;
    
    @NotBlank(message = "Service ID is required")
    private String serviceId;
    
    @NotNull(message = "Format must be specified")
    private LogFormat format;
    
    private String level;
    private String timestamp;
    private String host;
    
    private Long projectId;
    private String traceId;
    private String spanId;
}
