package com.loganalyzer.alert.repository;

import com.loganalyzer.alert.model.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    boolean existsBySubscriptionIdAndEventId(Long subscriptionId, String eventId);

    List<WebhookDelivery> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            WebhookDelivery.DeliveryStatus status, Instant now);

    List<WebhookDelivery> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    Optional<WebhookDelivery> findByIdAndSubscription_ProjectId(Long id, Long projectId);
}