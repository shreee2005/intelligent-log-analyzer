package com.loganalyzer.alert.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "webhook_deliveries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_webhook_delivery_event_subscription",
                columnNames = {"subscription_id", "event_id"}))
@Getter
@Setter
@NoArgsConstructor
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private WebhookSubscription subscription;

    @Column(nullable = false, length = 128)
    private String eventId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt = Instant.now();

    private Instant deliveredAt;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "latency_ms")
    private Long latencyMs;

    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public enum DeliveryStatus {
        PENDING, RETRY_PENDING, DELIVERED, FAILED
    }
}
