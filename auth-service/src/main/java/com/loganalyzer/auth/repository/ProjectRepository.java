package com.loganalyzer.auth.repository;

import com.loganalyzer.auth.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findAllByOwnerId(Long ownerId);
    Optional<Project> findByApiKey(String apiKey);
    Optional<Project> findByApiKeyHash(String apiKeyHash);
}
