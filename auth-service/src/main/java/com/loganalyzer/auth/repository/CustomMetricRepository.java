package com.loganalyzer.auth.repository;

import com.loganalyzer.auth.model.CustomMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomMetricRepository extends JpaRepository<CustomMetric, Long> {
    List<CustomMetric> findAllByProjectId(Long projectId);
}
