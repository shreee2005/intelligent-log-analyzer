package com.loganalyzer.alert.service;

import com.loganalyzer.alert.model.AnomalyEvent;
import com.loganalyzer.alert.model.WebhookDelivery;
import com.loganalyzer.alert.model.WebhookDlq;
import com.loganalyzer.alert.model.WebhookSubscription;
import com.loganalyzer.alert.repository.WebhookDeliveryRepository;
import com.loganalyzer.alert.repository.WebhookDlqRepository;
import com.loganalyzer.alert.repository.WebhookSubscriptionRepository;
import com.loganalyzer.alert.security.WebhookSsrfValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookDlqRepository dlqRepository;
    private final WebhookSignatureService signatures;
    private final WebhookSecretCrypto secretCrypto;
    private final WebhookSsrfValidator ssrfValidator;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private static final int MAX_ATTEMPTS = 5;
    private static final long[] RETRY_DELAYS_SECONDS = {0, 30, 120, 600, 1800};
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

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

        for (WebhookSubscription subscription : subscriptions.findAllByProjectIdAndActiveTrue(event.getProjectId())) {
            if (deliveries.existsBySubscriptionIdAndEventId(subscription.getId(), event.getEventId())) {
                log.debug("Duplicate event {} for subscription {}, skipping", event.getEventId(), subscription.getId());
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
        List<WebhookDelivery> due = deliveries.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                WebhookDelivery.DeliveryStatus.PENDING, Instant.now());
        due.addAll(deliveries.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                WebhookDelivery.DeliveryStatus.RETRY_PENDING, Instant.now()));

        for (WebhookDelivery delivery : due) {
            deliver(delivery);
        }
    }

    @Transactional
    public void deliver(WebhookDelivery delivery) {
        WebhookSubscription subscription = delivery.getSubscription();
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setStatus(WebhookDelivery.DeliveryStatus.RETRY_PENDING);

        Instant start = Instant.now();
        String errorMessage = null;
        Integer httpStatus = null;

        try {
            String url = subscription.getUrl();
            String secret = secretCrypto.decrypt(subscription.getSecret());

            var validation = ssrfValidator.validate(url);
            if (!validation.isValid()) {
                throw new IllegalArgumentException("SSRF validation failed: " + validation.error());
            }

            String signature = signatures.sign(secret, delivery.getPayload());

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(READ_TIMEOUT)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("X-Webhook-Event", "ANOMALY_DETECTED")
                    .header("X-Webhook-Delivery", delivery.getId().toString())
                    .header("X-Webhook-Signature", signature)
                    .header("X-Anomaly-Event-ID", delivery.getEventId())
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            httpStatus = response.statusCode();

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus(WebhookDelivery.DeliveryStatus.DELIVERED);
                delivery.setDeliveredAt(Instant.now());
                delivery.setLastError(null);
                recordMetrics(true, delivery.getAttempts(), httpStatus, Duration.between(start, Instant.now()));
            } else if (isRetryable(response.statusCode())) {
                scheduleRetry(delivery, "HTTP " + response.statusCode() + ": " + response.body());
            } else {
                delivery.setStatus(WebhookDelivery.DeliveryStatus.FAILED);
                delivery.setDeliveredAt(Instant.now());
                delivery.setLastError("Non-retryable HTTP " + response.statusCode() + ": " + response.body());
                delivery.setHttpStatus(response.statusCode());
                recordMetrics(false, delivery.getAttempts(), httpStatus, Duration.between(start, Instant.now()));
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Webhook delivery failed id={} attempt={} error={}", delivery.getId(), delivery.getAttempts(), errorMessage);
            handleException(delivery, errorMessage);
        } catch (Exception e) {
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Webhook delivery error id={} attempt={} error={}", delivery.getId(), delivery.getAttempts(), errorMessage);
            handleException(delivery, errorMessage);
        } finally {
            delivery.setHttpStatus(httpStatus);
            delivery.setLatencyMs(Duration.between(start, Instant.now()).toMillis());
            deliveries.save(delivery);
        }
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 408 || statusCode == 429 || (statusCode >= 500 && statusCode < 600);
    }

    private void scheduleRetry(WebhookDelivery delivery, String error) {
        if (delivery.getAttempts() >= MAX_ATTEMPTS) {
            moveToDlq(delivery, error);
        } else {
            delivery.setStatus(WebhookDelivery.DeliveryStatus.RETRY_PENDING);
            long delay = RETRY_DELAYS_SECONDS[Math.min(delivery.getAttempts(), RETRY_DELAYS_SECONDS.length - 1)];
            delivery.setNextAttemptAt(Instant.now().plusSeconds(delay));
            delivery.setLastError(error);
            log.info("Webhook delivery scheduled for retry id={} attempt={} delay={}s",
                    delivery.getId(), delivery.getAttempts(), delay);
        }
    }

    private void handleException(WebhookDelivery delivery, String error) {
        if (delivery.getAttempts() >= MAX_ATTEMPTS) {
            moveToDlq(delivery, error);
        } else {
            delivery.setStatus(WebhookDelivery.DeliveryStatus.RETRY_PENDING);
            long delay = RETRY_DELAYS_SECONDS[Math.min(delivery.getAttempts(), RETRY_DELAYS_SECONDS.length - 1)];
            delivery.setNextAttemptAt(Instant.now().plusSeconds(delay));
            delivery.setLastError(error);
        }
    }

    private void moveToDlq(WebhookDelivery delivery, String error) {
        delivery.setStatus(WebhookDelivery.DeliveryStatus.FAILED);
        delivery.setDeliveredAt(Instant.now());
        delivery.setLastError(error);

        WebhookDlq dlq = new WebhookDlq();
        dlq.setSubscriptionId(delivery.getSubscription().getId());
        dlq.setEventId(delivery.getEventId());
        dlq.setPayload(delivery.getPayload());
        dlq.setAttempts(delivery.getAttempts());
        dlq.setLastHttpStatus(delivery.getHttpStatus());
        dlq.setLastError(error);
        dlq.setOriginalCreatedAt(delivery.getCreatedAt() != null ? delivery.getCreatedAt() : Instant.now());
        dlqRepository.save(dlq);

        meterRegistry.counter("webhook_dlq_total", "subscription", delivery.getSubscription().getId().toString()).increment();
        log.warn("Webhook delivery moved to DLQ id={} after {} attempts", delivery.getId(), delivery.getAttempts());
    }

    private void recordMetrics(boolean success, int attempt, Integer httpStatus, Duration latency) {
        String statusTag = success ? "success" : "failure";
        meterRegistry.counter("webhook_delivery_total", "status", statusTag).increment();
        meterRegistry.counter("webhook_delivery_attempt_total", "attempt", String.valueOf(attempt)).increment();
        if (httpStatus != null) {
            meterRegistry.counter("webhook_delivery_http_status_total", "status", String.valueOf(httpStatus)).increment();
        }
        Timer.builder("webhook_delivery_latency_seconds")
                .tag("status", statusTag)
                .register(meterRegistry)
                .record(latency);
    }

    @Transactional
    public WebhookDelivery replayDelivery(Long deliveryId, Long projectId) {
        WebhookDelivery delivery = deliveries.findByIdAndSubscription_ProjectId(deliveryId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found or access denied"));

        if (delivery.getStatus() == WebhookDelivery.DeliveryStatus.DELIVERED) {
            throw new IllegalStateException("Cannot replay already delivered webhook");
        }

        delivery.setStatus(WebhookDelivery.DeliveryStatus.PENDING);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setLastError(null);
        delivery.setAttempts(0);
        WebhookDelivery saved = deliveries.save(delivery);

        meterRegistry.counter("webhook_delivery_replay_total").increment();
        log.info("Webhook delivery replayed id={}", deliveryId);
        return saved;
    }
}