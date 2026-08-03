package com.loganalyzer.ingestion.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String RAW_LOGS_TOPIC = "logs.raw";
    public static final String ERROR_LOGS_TOPIC = "logs.errors";

    @Bean
    public NewTopic rawLogsTopic() {
        return TopicBuilder.name(RAW_LOGS_TOPIC)
                .partitions(12)
                .replicas(1) // Set to 1 for local docker environment, would be 3 in prod
                .config("retention.ms", "604800000") // 7 days
                .build();
    }

    @Bean
    public NewTopic errorLogsTopic() {
        return TopicBuilder.name(ERROR_LOGS_TOPIC)
                .partitions(6)
                .replicas(1)
                .config("retention.ms", "2592000000") // 30 days
                .build();
    }
}
