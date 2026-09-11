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
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final LogSearchRepository logSearchRepository;
    private final ProjectAccessClient projectAccessClient;

    @GetMapping
    public ResponseEntity<?> searchLogs(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam Long projectId,
            @RequestParam String query) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Project access denied");
        }
        return ResponseEntity.ok(
                logSearchRepository.findByProjectIdAndMessageContaining(projectId, query));
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
}
