package com.loganalyzer.alert.controller;

import com.loganalyzer.alert.model.WebhookDelivery;
import com.loganalyzer.alert.model.WebhookSubscription;
import com.loganalyzer.alert.repository.WebhookDeliveryRepository;
import com.loganalyzer.alert.repository.WebhookSubscriptionRepository;
import com.loganalyzer.alert.security.ProjectAccessClient;
import com.loganalyzer.alert.service.WebhookSecretCrypto;
import com.loganalyzer.alert.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects/{projectId}/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookService webhookService;
    private final ProjectAccessClient projectAccessClient;
    private final WebhookSecretCrypto secretCrypto;

    @PostMapping
    public ResponseEntity<?> create(
            @PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateWebhookRequest request) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(403).body(Map.of("error", "Project access denied"));
        }
        if (!isHttpUrl(request.url())
                || request.secret() == null || request.secret().length() < 32) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "A URL and a secret of at least 32 characters are required"));
        }

        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setProjectId(projectId);
        subscription.setUrl(request.url());
        subscription.setSecret(secretCrypto.encrypt(request.secret()));
        WebhookSubscription saved = subscriptions.save(subscription);
        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "projectId", saved.getProjectId(),
                "url", saved.getUrl(),
                "active", saved.isActive()));
    }

    @GetMapping
    public ResponseEntity<?> list(
            @PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(403).body(Map.of("error", "Project access denied"));
        }
        List<WebhookSubscription> list = subscriptions.findAllByProjectIdAndActiveTrue(projectId);
        return ResponseEntity.ok(list.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{webhookId}")
    public ResponseEntity<?> get(
            @PathVariable Long projectId,
            @PathVariable Long webhookId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(403).body(Map.of("error", "Project access denied"));
        }
        return subscriptions.findByIdAndProjectId(webhookId, projectId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{webhookId}")
    public ResponseEntity<?> update(
            @PathVariable Long projectId,
            @PathVariable Long webhookId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UpdateWebhookRequest request) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(403).body(Map.of("error", "Project access denied"));
        }
        return subscriptions.findByIdAndProjectId(webhookId, projectId)
                .map(webhook -> {
                    if (request.url() != null) {
                        if (!isHttpUrl(request.url())) {
                            return ResponseEntity.badRequest().body(Map.of("error", "Invalid URL"));
                        }
                        webhook.setUrl(request.url());
                    }
                    if (request.secret() != null) {
                        if (request.secret().length() < 32) {
                            return ResponseEntity.badRequest().body(
                                    Map.of("error", "Secret must be at least 32 characters"));
                        }
                        webhook.setSecret(secretCrypto.encrypt(request.secret()));
                    }
                    if (request.active() != null) {
                        webhook.setActive(request.active());
                    }
                    webhook.setUpdatedAt(java.time.Instant.now());
                    WebhookSubscription saved = subscriptions.save(webhook);
                    return ResponseEntity.ok(toResponse(saved));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{webhookId}")
    public ResponseEntity<?> disable(
            @PathVariable Long projectId,
            @PathVariable Long webhookId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(403).body(Map.of("error", "Project access denied"));
        }
        return subscriptions.findByIdAndProjectId(webhookId, projectId)
                .map(webhook -> {
                    webhook.setActive(false);
                    webhook.setUpdatedAt(java.time.Instant.now());
                    subscriptions.save(webhook);
                    return ResponseEntity.ok(Map.of("message", "Webhook disabled"));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{webhookId}/deliveries")
    public ResponseEntity<?> getDeliveries(
            @PathVariable Long projectId,
            @PathVariable Long webhookId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(403).body(Map.of("error", "Project access denied"));
        }
        if (!subscriptions.existsByIdAndProjectId(webhookId, projectId)) {
            return ResponseEntity.notFound().build();
        }
        List<WebhookDelivery> list = deliveries.findBySubscriptionIdOrderByCreatedAtDesc(webhookId);
        return ResponseEntity.ok(list.stream().map(this::toDeliveryResponse).collect(Collectors.toList()));
    }

    @PostMapping("/deliveries/{deliveryId}/replay")
    public ResponseEntity<?> replayDelivery(
            @PathVariable Long projectId,
            @PathVariable Long deliveryId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(403).body(Map.of("error", "Project access denied"));
        }
        try {
            WebhookDelivery replayed = webhookService.replayDelivery(deliveryId, projectId);
            return ResponseEntity.ok(toDeliveryResponse(replayed));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record CreateWebhookRequest(String url, String secret) {}
    public record UpdateWebhookRequest(String url, String secret, Boolean active) {}

    private Map<String, Object> toResponse(WebhookSubscription w) {
        return Map.of(
                "id", w.getId(),
                "projectId", w.getProjectId(),
                "url", w.getUrl(),
                "active", w.isActive(),
                "createdAt", w.getCreatedAt(),
                "updatedAt", w.getUpdatedAt());
    }

    private Map<String, Object> toDeliveryResponse(WebhookDelivery d) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", d.getId());
        map.put("subscriptionId", d.getSubscription().getId());
        map.put("eventId", d.getEventId());
        map.put("status", d.getStatus().name());
        map.put("attempts", d.getAttempts());
        map.put("httpStatus", d.getHttpStatus());
        map.put("latencyMs", d.getLatencyMs());
        map.put("nextAttemptAt", d.getNextAttemptAt());
        map.put("deliveredAt", d.getDeliveredAt());
        map.put("lastError", d.getLastError());
        map.put("createdAt", d.getCreatedAt());
        return map;
    }

    private boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            String scheme = URI.create(value).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}