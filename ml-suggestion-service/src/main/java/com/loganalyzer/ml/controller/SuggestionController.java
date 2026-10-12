package com.loganalyzer.ml.controller;

import com.loganalyzer.ml.dto.MlAnalysisRequest;
import com.loganalyzer.ml.dto.MlAnalysisResponse;
import com.loganalyzer.ml.dto.SuggestionResponse;
import com.loganalyzer.ml.service.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;

    @PostMapping("/analyze")
    public ResponseEntity<MlAnalysisResponse> analyze(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody MlAnalysisRequest request) {
        
        if (authorization == null || authorization.isBlank()) {
            return ResponseEntity.status(401).body(
                    MlAnalysisResponse.builder()
                            .status("ERROR")
                            .message("Authorization header required")
                            .build());
        }

        String jwtToken = authorization.replace("Bearer ", "");
        
        SuggestionResponse response = suggestionService.analyze(request, jwtToken);
        
        if ("FAILED".equals(response.getStatus())) {
            return ResponseEntity.status(500).body(
                    MlAnalysisResponse.builder()
                            .suggestionId(response.getSuggestionId())
                            .status("FAILED")
                            .message(response.getErrorMessage())
                            .build());
        }

        return ResponseEntity.ok(
                MlAnalysisResponse.builder()
                        .suggestionId(response.getSuggestionId())
                        .status("COMPLETED")
                        .message("Analysis completed successfully")
                        .build());
    }

    @GetMapping("/{suggestionId}")
    public ResponseEntity<SuggestionResponse> getSuggestion(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String suggestionId) {
        
        if (authorization == null || authorization.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        SuggestionResponse response = suggestionService.getSuggestion(suggestionId);
        
        if (response == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SuggestionResponse>> getSuggestions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String anomalyId,
            @RequestParam(required = false) Long projectId) {
        
        if (authorization == null || authorization.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        List<SuggestionResponse> suggestions;
        
        if (anomalyId != null) {
            suggestions = suggestionService.getSuggestionsByAnomaly(anomalyId);
        } else if (projectId != null) {
            suggestions = suggestionService.getSuggestionsByProject(projectId);
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(suggestions);
    }
}