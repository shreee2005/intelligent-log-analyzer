package com.loganalyzer.search.scheduler;

import com.loganalyzer.search.repository.LogSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class DataRetentionScheduler {

    private final LogSearchRepository logSearchRepository;

    // Run this cleanup job every day at midnight (cron format)
    // For testing/demonstration, we can just run it every hour: "0 0 * * * *"
    // It deletes logs older than 7 days to save Elasticsearch storage costs.
    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupOldLogs() {
        log.info("Starting scheduled data retention cleanup...");
        
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        
        try {
            logSearchRepository.deleteByTimestampBefore(sevenDaysAgo);
            log.info("Successfully deleted logs older than 7 days ({})", sevenDaysAgo);
        } catch (Exception e) {
            log.error("Failed to execute data retention cleanup policy", e);
        }
    }
}
