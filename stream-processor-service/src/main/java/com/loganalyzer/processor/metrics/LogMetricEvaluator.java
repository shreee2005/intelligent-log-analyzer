package com.loganalyzer.processor.metrics;

import com.loganalyzer.processor.model.CustomMetric;
import com.loganalyzer.processor.model.LogEntry;
import com.loganalyzer.processor.repository.CustomMetricRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Component
public class LogMetricEvaluator {

    private final CustomMetricRepository customMetricRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final List<CachedMetric> cachedMetrics = new CopyOnWriteArrayList<>();

    private static final DateTimeFormatter REDIS_KEY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    public LogMetricEvaluator(CustomMetricRepository customMetricRepository, RedisTemplate<String, String> redisTemplate) {
        this.customMetricRepository = customMetricRepository;
        this.redisTemplate = redisTemplate;
        // Run initial load
        loadMetricsCache();
    }

    @Scheduled(fixedDelay = 10000) // refresh metrics every 10 seconds
    public void loadMetricsCache() {
        try {
            List<CustomMetric> dbMetrics = customMetricRepository.findAll();
            List<CachedMetric> newCache = new CopyOnWriteArrayList<>();
            for (CustomMetric m : dbMetrics) {
                try {
                    Pattern pattern = Pattern.compile(m.getRegexPattern());
                    newCache.add(new CachedMetric(m.getId(), m.getProjectId(), pattern));
                } catch (PatternSyntaxException e) {
                    log.error("Invalid regex pattern for metric {}: {}", m.getName(), m.getRegexPattern(), e);
                }
            }
            cachedMetrics.clear();
            cachedMetrics.addAll(newCache);
        } catch (Exception e) {
            log.error("Failed to load custom metrics from PostgreSQL", e);
        }
    }

    public void evaluateAndIncrement(LogEntry logEntry) {
        if (logEntry == null || logEntry.getProjectId() == null || logEntry.getMessage() == null) {
            return;
        }

        Long logProjectId = logEntry.getProjectId();
        String message = logEntry.getMessage();

        for (CachedMetric metric : cachedMetrics) {
            if (metric.getProjectId().equals(logProjectId)) {
                if (metric.getPattern().matcher(message).matches()) {
                    try {
                        Instant bucketTime = logEntry.getTimestamp();
                        String timeStr = REDIS_KEY_FORMATTER.format(bucketTime);
                        String redisKey = String.format("metrics:%d:%d:%s", logProjectId, metric.getId(), timeStr);
                        
                        redisTemplate.opsForValue().increment(redisKey);
                        redisTemplate.expire(redisKey, Duration.ofHours(24));
                    } catch (Exception e) {
                        log.error("Failed to increment metric counter in Redis for key template metrics:{}:{}", logProjectId, metric.getId(), e);
                    }
                }
            }
        }
    }

    private static class CachedMetric {
        private final Long id;
        private final Long projectId;
        private final Pattern pattern;

        public CachedMetric(Long id, Long projectId, Pattern pattern) {
            this.id = id;
            this.projectId = projectId;
            this.pattern = pattern;
        }

        public Long getId() {
            return id;
        }

        public Long getProjectId() {
            return projectId;
        }

        public Pattern getPattern() {
            return pattern;
        }
    }
}
