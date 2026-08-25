package com.loganalyzer.auth.controller;

import com.loganalyzer.auth.model.CustomMetric;
import com.loganalyzer.auth.repository.CustomMetricRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@RestController
@RequestMapping("/api/projects/{projectId}/metrics")
public class CustomMetricController {

    private final CustomMetricRepository customMetricRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private static final DateTimeFormatter REDIS_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    public CustomMetricController(CustomMetricRepository customMetricRepository, RedisTemplate<String, String> redisTemplate) {
        this.customMetricRepository = customMetricRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long projectId, @RequestBody Map<String, String> request) {
        String name = request.get("name");
        String regexPattern = request.get("regexPattern");

        if (name == null || name.trim().isEmpty() || regexPattern == null || regexPattern.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name and regexPattern are required"));
        }

        try {
            Pattern.compile(regexPattern);
        } catch (PatternSyntaxException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid Java regular expression. Example: .*MOCK EMAIL SENT.*"
            ));
        }

        CustomMetric metric = new CustomMetric();
        metric.setProjectId(projectId);
        metric.setName(name);
        metric.setRegexPattern(regexPattern);
        customMetricRepository.save(metric);

        return ResponseEntity.ok(metric);
    }

    @GetMapping
    public ResponseEntity<List<CustomMetric>> list(@PathVariable Long projectId) {
        List<CustomMetric> metrics = customMetricRepository.findAllByProjectId(projectId);
        return ResponseEntity.ok(metrics);
    }

    @DeleteMapping("/{metricId}")
    public ResponseEntity<?> delete(@PathVariable Long projectId, @PathVariable Long metricId) {
        Optional<CustomMetric> metricOpt = customMetricRepository.findById(metricId);
        if (metricOpt.isEmpty() || !metricOpt.get().getProjectId().equals(projectId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Metric not found"));
        }

        customMetricRepository.deleteById(metricId);
        return ResponseEntity.ok(Map.of("message", "Metric deleted"));
    }

    @GetMapping("/{metricId}/data")
    public ResponseEntity<?> getTimeseriesData(
            @PathVariable Long projectId,
            @PathVariable Long metricId,
            @RequestParam(defaultValue = "60") int minutes) {

        Optional<CustomMetric> metricOpt = customMetricRepository.findById(metricId);
        if (metricOpt.isEmpty() || !metricOpt.get().getProjectId().equals(projectId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Metric not found"));
        }

        List<Map<String, Object>> chartPoints = new ArrayList<>();
        Instant nowMinute = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        for (int i = minutes - 1; i >= 0; i--) {
            Instant pointTime = nowMinute.minus(i, ChronoUnit.MINUTES);
            String redisTimeStr = REDIS_TIME_FORMATTER.format(pointTime);

            String key = String.format("metrics:%d:%d:%s", projectId, metricId, redisTimeStr);
            String rawVal = redisTemplate.opsForValue().get(key);
            long count = 0;
            if (rawVal != null) {
                try {
                    count = Long.parseLong(rawVal);
                } catch (NumberFormatException ignored) {}
            }

            Map<String, Object> point = new HashMap<>();
            point.put("timestamp", pointTime.toString());
            point.put("count", count);
            chartPoints.add(point);
        }

        return ResponseEntity.ok(chartPoints);
    }
}
