package com.loganalyzer.ml.service;

import com.loganalyzer.ml.model.MlSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionCacheService {

    private final RedisTemplate<String, MlSuggestion> redisTemplate;

    private static final String CACHE_PREFIX = "ml:suggestion:";

    public Optional<MlSuggestion> getOptional(String anomalyId, String promptHash) {
        String key = CACHE_PREFIX + anomalyId + ":" + promptHash;
        try {
            MlSuggestion suggestion = redisTemplate.opsForValue().get(key);
            if (suggestion != null) {
                log.debug("Cache hit for key: {}", key);
                return Optional.of(suggestion);
            }
            log.debug("Cache miss for key: {}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Cache get failed for key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public MlSuggestion get(String anomalyId, String promptHash) {
        return getOptional(anomalyId, promptHash).orElse(null);
    }

    public void put(String anomalyId, String promptHash, MlSuggestion suggestion, long ttlSeconds) {
        String key = CACHE_PREFIX + anomalyId + ":" + promptHash;
        try {
            redisTemplate.opsForValue().set(key, suggestion, Duration.ofSeconds(ttlSeconds));
            log.debug("Cached suggestion for key: {} with TTL {}s", key, ttlSeconds);
        } catch (Exception e) {
            log.warn("Cache put failed for key {}: {}", key, e.getMessage());
        }
    }

    public void evict(String anomalyId, String promptHash) {
        String key = CACHE_PREFIX + anomalyId + ":" + promptHash;
        try {
            redisTemplate.delete(key);
            log.debug("Evicted cache for key: {}", key);
        } catch (Exception e) {
            log.warn("Cache evict failed for key {}: {}", key, e.getMessage());
        }
    }

    public void evictByAnomalyId(String anomalyId) {
        try {
            String pattern = CACHE_PREFIX + anomalyId + ":*";
            redisTemplate.keys(pattern).forEach(redisTemplate::delete);
            log.debug("Evicted all cache entries for anomalyId: {}", anomalyId);
        } catch (Exception e) {
            log.warn("Cache evict by anomalyId failed for {}: {}", anomalyId, e.getMessage());
        }
    }

    public long getCacheSize() {
        try {
            return redisTemplate.keys(CACHE_PREFIX + "*").size();
        } catch (Exception e) {
            log.warn("Failed to get cache size: {}", e.getMessage());
            return 0;
        }
    }
}