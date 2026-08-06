package com.loganalyzer.analysis.repository;

import com.loganalyzer.analysis.model.DashboardMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class MetricsRedisRepository {

    private final StringRedisTemplate redisTemplate;
    
    // e.g. metrics:payment-service:2026-08-05T12:00
    private static final String KEY_PREFIX = "metrics:";
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                                                                            .withZone(ZoneId.of("UTC"));

    public void incrementLogCount(String serviceId, String level, Instant timestamp) {
        String timeBucket = MINUTE_FORMAT.format(timestamp);
        String key = KEY_PREFIX + serviceId + ":" + timeBucket;
        
        // Track the buckets for the service so we can easily query them later
        redisTemplate.opsForSet().add("service_buckets:" + serviceId, timeBucket);
        
        redisTemplate.opsForHash().increment(key, "TOTAL", 1);
        
        if ("ERROR".equalsIgnoreCase(level) || "FATAL".equalsIgnoreCase(level)) {
            redisTemplate.opsForHash().increment(key, "ERROR", 1);
        } else if ("WARN".equalsIgnoreCase(level)) {
            redisTemplate.opsForHash().increment(key, "WARN", 1);
        }
        
        // Expire bucket after 24 hours
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
    }

    public DashboardMetrics getMetrics(String serviceId) {
        Set<String> buckets = redisTemplate.opsForSet().members("service_buckets:" + serviceId);
        
        long totalLogs = 0;
        long errorCount = 0;
        long warningCount = 0;
        Map<String, Long> timeline = new HashMap<>();
        
        if (buckets != null) {
            for (String bucket : buckets) {
                String key = KEY_PREFIX + serviceId + ":" + bucket;
                
                Object totalObj = redisTemplate.opsForHash().get(key, "TOTAL");
                Object errorObj = redisTemplate.opsForHash().get(key, "ERROR");
                Object warnObj = redisTemplate.opsForHash().get(key, "WARN");
                
                long bucketTotal = totalObj != null ? Long.parseLong((String) totalObj) : 0;
                long bucketError = errorObj != null ? Long.parseLong((String) errorObj) : 0;
                long bucketWarn = warnObj != null ? Long.parseLong((String) warnObj) : 0;
                
                totalLogs += bucketTotal;
                errorCount += bucketError;
                warningCount += bucketWarn;
                
                timeline.put(bucket, bucketError); // Plot errors over time
            }
        }
        
        double errorRate = totalLogs > 0 ? (double) errorCount / totalLogs : 0.0;
        
        return DashboardMetrics.builder()
                .serviceId(serviceId)
                .totalLogs(totalLogs)
                .errorCount(errorCount)
                .warningCount(warningCount)
                .errorRate(errorRate)
                .timeline(timeline)
                .build();
    }
}
