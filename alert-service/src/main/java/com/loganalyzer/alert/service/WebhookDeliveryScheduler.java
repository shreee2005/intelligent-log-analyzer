package com.loganalyzer.alert.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookDeliveryScheduler {

    private final WebhookService webhookService;

    @Scheduled(fixedDelayString = "${webhook.delivery.interval-ms:5000}")
    public void deliverDueWebhooks() {
        webhookService.deliverDue();
    }
}
