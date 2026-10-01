package com.loganalyzer.alert.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "webhook_dlq")
@Getter
@Setter
@NoArgsConstructor
public class WebhookDlq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt = Instant.now();

    @Column(name = "original_created_at", nullable = false)
    private Instant originalCreatedAt;
}