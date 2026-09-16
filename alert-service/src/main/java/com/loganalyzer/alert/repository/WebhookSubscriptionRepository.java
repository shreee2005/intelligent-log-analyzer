package com.loganalyzer.alert.repository;

import com.loganalyzer.alert.model.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {
    List<WebhookSubscription> findAllByProjectIdAndActiveTrue(Long projectId);
}
