package com.loganalyzer.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.analysis.model.VolumeAnomaly;
import com.loganalyzer.analysis.repository.MetricsRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class AnomalyDetectionService {

    private final MetricsRedisRepository metricsRedisRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String ANOMALY_TOPIC = "logs.anomalies";

    // Run every minute
    @Scheduled(fixedDelay = 60000)
    public void detectAnomalies() {
        log.info("Running ML Volume Anomaly Detection (Z-Score)");

        Set<String> bucketKeys = metricsRedisRepository.getAllServiceBucketKeys();
        if (bucketKeys == null || bucketKeys.isEmpty()) {
            return;
        }

        Instant currentMinute = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        // We look at the previous minute to ensure it's fully complete
        Instant evalMinute = currentMinute.minus(1, ChronoUnit.MINUTES);

        for (String key : bucketKeys) {
            // key format: service_buckets:{projectId}:{serviceId}
            String[] parts = key.split(":");
            if (parts.length < 3) continue;

            Long projectId = Long.parseLong(parts[1]);
            String serviceId = parts[2];

            List<Long> historicalVolumes = metricsRedisRepository.getVolumesForLast24Hours(projectId, serviceId);
            
            if (historicalVolumes.size() < 10) {
                // Not enough data points to calculate a meaningful baseline
                continue;
            }

            double mean = calculateMean(historicalVolumes);
            double stdDev = calculateStdDev(historicalVolumes, mean);

            long currentVolume = metricsRedisRepository.getVolumeForMinute(projectId, serviceId, evalMinute);

            double zScore = 0;
            if (stdDev > 0) {
                zScore = (currentVolume - mean) / stdDev;
            } else if (currentVolume > mean) {
                zScore = 10; // arbitrary high score if there is no variance but sudden spike
            }

            if (zScore > 3 || zScore < -3) {
                String type = zScore > 3 ? "SPIKE" : "DROP";
                
                // If it's a drop but mean is already near 0, ignore to prevent noisy false positives
                if (type.equals("DROP") && mean < 5) {
                    continue;
                }

                VolumeAnomaly anomaly = VolumeAnomaly.builder()
                        .schemaVersion(1)
                        .eventType("ANOMALY_DETECTED")
                        .eventId(UUID.randomUUID())
                        .projectId(projectId)
                        .serviceId(serviceId)
                        .detector("VOLUME_Z_SCORE")
                        .severity(zScore > 5 || zScore < -5 ? "CRITICAL" : "HIGH")
                        .deduplicationKey(String.format("%s:%s:volume:%s",
                                projectId, serviceId, evalMinute))
                        .timestamp(evalMinute)
                        .zScore(zScore)
                        .currentVolume(currentVolume)
                        .meanVolume(mean)
                        .stdDev(stdDev)
                        .type(type)
                        .build();

                publishAnomaly(anomaly);
            }
        }
    }

    private double calculateMean(List<Long> data) {
        return data.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    private double calculateStdDev(List<Long> data, double mean) {
        double variance = data.stream()
                .mapToDouble(d -> Math.pow(d - mean, 2))
                .average().orElse(0.0);
        return Math.sqrt(variance);
    }

    private void publishAnomaly(VolumeAnomaly anomaly) {
        try {
            String payload = objectMapper.writeValueAsString(anomaly);
            kafkaTemplate.send(ANOMALY_TOPIC, payload);
            
            // Save to Redis for frontend retrieval (keep last 50)
            metricsRedisRepository.saveAnomaly(anomaly.getProjectId(), payload);
            
            log.warn("🚨 Volume Anomaly Detected! Service: {}, Type: {}, Z-Score: {}", 
                    anomaly.getServiceId(), anomaly.getType(), String.format("%.2f", anomaly.getZScore()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize VolumeAnomaly", e);
        }
    }
}
