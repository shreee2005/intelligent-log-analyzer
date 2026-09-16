package com.loganalyzer.alert.repository;

import com.loganalyzer.alert.model.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    boolean existsBySubscriptionIdAndEventId(Long subscriptionId, String eventId);

    List<WebhookDelivery> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            WebhookDelivery.DeliveryStatus status, Instant now);
}
