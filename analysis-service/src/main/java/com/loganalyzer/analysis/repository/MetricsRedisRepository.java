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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class MetricsRedisRepository {

    private final StringRedisTemplate redisTemplate;
    
    // e.g. metrics:payment-service:2026-08-05T12:00
    private static final String KEY_PREFIX = "metrics:";
    private static final String SERVICE_INDEX_KEY = "service_bucket_indexes";
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                                                                            .withZone(ZoneId.of("UTC"));

    public void incrementLogCount(Long projectId, String serviceId, String level, Instant timestamp) {
        String timeBucket = MINUTE_FORMAT.format(timestamp);
        long pid = projectId != null ? projectId : 0L;
        String key = KEY_PREFIX + pid + ":" + serviceId + ":" + timeBucket;
        
        // Track the buckets for the service so we can easily query them later
        String serviceBucketKey = serviceBucketKey(pid, serviceId);
        redisTemplate.opsForSet().add(serviceBucketKey, timeBucket);
        redisTemplate.opsForSet().add(SERVICE_INDEX_KEY, serviceBucketKey);
        
        redisTemplate.opsForHash().increment(key, "TOTAL", 1);
        
        if ("ERROR".equalsIgnoreCase(level) || "FATAL".equalsIgnoreCase(level)) {
            redisTemplate.opsForHash().increment(key, "ERROR", 1);
        } else if ("WARN".equalsIgnoreCase(level)) {
            redisTemplate.opsForHash().increment(key, "WARN", 1);
        }
        
        // Expire bucket after 24 hours
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
        redisTemplate.expire(serviceBucketKey, 25, TimeUnit.HOURS);
        redisTemplate.expire(SERVICE_INDEX_KEY, 25, TimeUnit.HOURS);
    }

    public DashboardMetrics getMetrics(Long projectId, String serviceId) {
        long pid = projectId != null ? projectId : 0L;
        Set<String> buckets = activeBuckets(pid, serviceId);
        
        long totalLogs = 0;
        long errorCount = 0;
        long warningCount = 0;
        Map<String, Long> timeline = new HashMap<>();
        
        if (buckets != null) {
            for (String bucket : buckets) {
                String key = KEY_PREFIX + pid + ":" + serviceId + ":" + bucket;
                
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

    public Set<String> getAllServiceBucketKeys() {
        Set<String> indexes = redisTemplate.opsForSet().members(SERVICE_INDEX_KEY);
        return indexes == null ? Set.of() : indexes;
    }

    public java.util.List<Long> getVolumesForLast24Hours(Long projectId, String serviceId) {
        long pid = projectId != null ? projectId : 0L;
        List<Long> volumes = new ArrayList<>();
        Instant cutoff = Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS);
        Set<String> buckets = activeBuckets(pid, serviceId);
        if (buckets != null) {
            for (String bucket : buckets) {
                Instant bucketTime = parseBucket(bucket);
                if (bucketTime == null || bucketTime.isBefore(cutoff)) {
                    continue;
                }
                String key = KEY_PREFIX + pid + ":" + serviceId + ":" + bucket;
                Object totalObj = redisTemplate.opsForHash().get(key, "TOTAL");
                volumes.add(totalObj == null ? 0L : Long.parseLong((String) totalObj));
            }
        }
        return volumes;
    }

    public long getVolumeForMinute(Long projectId, String serviceId, Instant minute) {
        long pid = projectId != null ? projectId : 0L;
        String timeBucket = MINUTE_FORMAT.format(minute);
        String key = KEY_PREFIX + pid + ":" + serviceId + ":" + timeBucket;
        Object totalObj = redisTemplate.opsForHash().get(key, "TOTAL");
        return totalObj != null ? Long.parseLong((String) totalObj) : 0L;
    }

    public void saveAnomaly(Long projectId, String payload) {
        String redisKey = "anomalies:" + (projectId != null ? projectId : 0L);
        redisTemplate.opsForList().leftPush(redisKey, payload);
        redisTemplate.opsForList().trim(redisKey, 0, 49);
        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);
    }

    public boolean markAnomalyIfNew(String deduplicationKey) {
        String key = "anomaly_dedup:" + deduplicationKey;
        Boolean added = redisTemplate.opsForValue().setIfAbsent(key, "1", 24, TimeUnit.HOURS);
        return Boolean.TRUE.equals(added);
    }

    private Set<String> activeBuckets(Long projectId, String serviceId) {
        long pid = projectId != null ? projectId : 0L;
        Set<String> buckets = redisTemplate.opsForSet().members(serviceBucketKey(pid, serviceId));
        if (buckets == null) {
            return Set.of();
        }
        Instant cutoff = Instant.now().minus(25, java.time.temporal.ChronoUnit.HOURS);
        buckets.removeIf(bucket -> {
            Instant bucketTime = parseBucket(bucket);
            return bucketTime == null || bucketTime.isBefore(cutoff);
        });
        return buckets;
    }

    private String serviceBucketKey(long projectId, String serviceId) {
        return "service_buckets:" + projectId + ":" + serviceId;
    }

    private Instant parseBucket(String bucket) {
        try {
            return Instant.from(MINUTE_FORMAT.parse(bucket));
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
