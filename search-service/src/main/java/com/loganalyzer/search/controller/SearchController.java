package com.loganalyzer.search.controller;

import com.loganalyzer.search.model.LogDocument;
import com.loganalyzer.search.repository.LogSearchRepository;
import com.loganalyzer.search.security.ProjectAccessClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController

@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final LogSearchRepository logSearchRepository;
    private final ProjectAccessClient projectAccessClient;

    @GetMapping
    public ResponseEntity<?> searchLogs(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam Long projectId,
            @RequestParam(required = false) String query) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Project access denied");
        }
        if (query == null || query.isBlank()) {
            return ResponseEntity.ok(
                    logSearchRepository.findByProjectIdOrderByTimestampDesc(projectId));
        }
        return ResponseEntity.ok(
                logSearchRepository.findByProjectIdAndMessageContaining(projectId, query));
    }
    
    @GetMapping("/services")
    public ResponseEntity<?> getServices(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam Long projectId) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Project access denied");
        }
        Set<String> services = logSearchRepository.findDistinctServiceIdByProjectId(projectId);
        return ResponseEntity.ok(services);
    }

    @GetMapping("/service")
    public ResponseEntity<?> searchByService(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam Long projectId,
            @RequestParam String serviceId) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Project access denied");
        }
        return ResponseEntity.ok(
                logSearchRepository.findByProjectIdAndServiceId(projectId, serviceId));
    }
    
    @GetMapping("/level")
    public ResponseEntity<?> searchByLevel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam Long projectId,
            @RequestParam String level) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Project access denied");
        }
        return ResponseEntity.ok(
                logSearchRepository.findByProjectIdAndLevel(projectId, level));
    }

    @GetMapping("/trace")
    public ResponseEntity<?> searchByTrace(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam Long projectId,
            @RequestParam String traceId) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Project access denied");
        }
        return ResponseEntity.ok(
                logSearchRepository.findByProjectIdAndTraceIdOrderByTimestampAsc(projectId, traceId));
    }
}
