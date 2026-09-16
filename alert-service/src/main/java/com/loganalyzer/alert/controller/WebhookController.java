package com.loganalyzer.alert.controller;

import com.loganalyzer.alert.model.WebhookSubscription;
import com.loganalyzer.alert.repository.WebhookSubscriptionRepository;
import com.loganalyzer.alert.security.ProjectAccessClient;
import com.loganalyzer.alert.service.WebhookSecretCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.net.URI;

@RestController
@RequestMapping("/api/projects/{projectId}/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookSubscriptionRepository subscriptions;
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

    @DeleteMapping("/{webhookId}")
    public ResponseEntity<?> disable(
            @PathVariable Long projectId,
            @PathVariable Long webhookId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!projectAccessClient.hasAccess(projectId, authorization)) {
            return ResponseEntity.status(403).body(Map.of("error", "Project access denied"));
        }
        return subscriptions.findById(webhookId)
                .filter(webhook -> webhook.getProjectId().equals(projectId))
                .map(webhook -> {
                    webhook.setActive(false);
                    subscriptions.save(webhook);
                    return ResponseEntity.ok(Map.of("message", "Webhook disabled"));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record CreateWebhookRequest(String url, String secret) {
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
