package com.loganalyzer.alert.service;

import com.loganalyzer.alert.model.AnomalyEvent;
import com.loganalyzer.alert.model.WebhookDelivery;
import com.loganalyzer.alert.model.WebhookSubscription;
import com.loganalyzer.alert.repository.WebhookDeliveryRepository;
import com.loganalyzer.alert.repository.WebhookSubscriptionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookSignatureService signatures;
    private final WebhookSecretCrypto secretCrypto;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Transactional
    public void enqueue(AnomalyEvent event) {
        if (event.getProjectId() == null || event.getEventId() == null) {
            return;
        }

        final String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize anomaly webhook payload", exception);
        }

        for (WebhookSubscription subscription :
                subscriptions.findAllByProjectIdAndActiveTrue(event.getProjectId())) {
            if (deliveries.existsBySubscriptionIdAndEventId(subscription.getId(), event.getEventId())) {
                continue;
            }
            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setSubscription(subscription);
            delivery.setEventId(event.getEventId());
            delivery.setPayload(payload);
            deliveries.save(delivery);
        }
    }

    @Transactional
    public void deliverDue() {
        List<WebhookDelivery> due = deliveries
                .findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        WebhookDelivery.DeliveryStatus.PENDING, Instant.now());
        for (WebhookDelivery delivery : due) {
            deliver(delivery);
        }
    }

    private void deliver(WebhookDelivery delivery) {
        WebhookSubscription subscription = delivery.getSubscription();
        delivery.setAttempts(delivery.getAttempts() + 1);
        try {
            restClientBuilder.build()
                    .post()
                    .uri(subscription.getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Webhook-Event", "ANOMALY_DETECTED")
                    .header("X-Webhook-Delivery", delivery.getId().toString())
                    .header("X-Webhook-Signature",
                            signatures.sign(secretCrypto.decrypt(subscription.getSecret()), delivery.getPayload()))
                    .body(delivery.getPayload())
                    .retrieve()
                    .toBodilessEntity();
            delivery.setStatus(WebhookDelivery.DeliveryStatus.DELIVERED);
            delivery.setDeliveredAt(Instant.now());
            delivery.setLastError(null);
        } catch (Exception exception) {
            delivery.setLastError(exception.getMessage());
            if (delivery.getAttempts() >= 5) {
                delivery.setStatus(WebhookDelivery.DeliveryStatus.FAILED);
            } else {
                long delaySeconds = Math.min(3600, 30L * (1L << (delivery.getAttempts() - 1)));
                delivery.setNextAttemptAt(Instant.now().plus(Duration.ofSeconds(delaySeconds)));
            }
            log.warn("Webhook delivery failed id={} attempt={}", delivery.getId(), delivery.getAttempts());
        }
        deliveries.save(delivery);
    }
}
