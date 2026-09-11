package com.loganalyzer.analysis.controller;

import lombok.RequiredArgsConstructor;
import com.loganalyzer.analysis.security.ProjectAccessClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/projects/{projectId}/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final StringRedisTemplate redisTemplate;
    private final ProjectAccessClient projectAccessClient;

    @GetMapping
    public ResponseEntity<?> getAnomalies(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long projectId) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Project access denied");
        }
        String redisKey = "anomalies:" + projectId;
        List<String> anomalies = redisTemplate.opsForList().range(redisKey, 0, -1);
        if (anomalies == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(anomalies);
    }
}
