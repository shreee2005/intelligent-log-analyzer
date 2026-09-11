package com.loganalyzer.processor.repository;

import com.loganalyzer.processor.model.CustomMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomMetricRepository extends JpaRepository<CustomMetric, Long> {
}
