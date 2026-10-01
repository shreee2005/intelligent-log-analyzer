package com.loganalyzer.alert.repository;

import com.loganalyzer.alert.model.WebhookDlq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WebhookDlqRepository extends JpaRepository<WebhookDlq, Long> {
    List<WebhookDlq> findBySubscriptionIdOrderByFailedAtDesc(Long subscriptionId);

    List<WebhookDlq> findByFailedAtAfter(Instant since);
}