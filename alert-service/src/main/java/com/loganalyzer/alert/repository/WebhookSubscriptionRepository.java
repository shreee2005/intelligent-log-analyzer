package com.loganalyzer.alert.repository;

import com.loganalyzer.alert.model.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {
    List<WebhookSubscription> findAllByProjectIdAndActiveTrue(Long projectId);

    Optional<WebhookSubscription> findByIdAndProjectId(Long id, Long projectId);

    boolean existsByIdAndProjectId(Long id, Long projectId);
}