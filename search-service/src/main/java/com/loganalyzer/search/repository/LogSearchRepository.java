package com.loganalyzer.search.repository;

import com.loganalyzer.search.model.LogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface LogSearchRepository extends ElasticsearchRepository<LogDocument, String> {
    
    List<LogDocument> findByProjectIdAndServiceId(Long projectId, String serviceId);
    
    List<LogDocument> findByProjectIdAndLevel(Long projectId, String level);
    
    // Fuzzy search using Elastic
    List<LogDocument> findByProjectIdAndMessageContaining(Long projectId, String keyword);
    
    List<LogDocument> findByTimestampBefore(Instant timestamp);
    
    // Deletes logs older than a specific time for our Data Retention Policy
    void deleteByTimestampBefore(Instant timestamp);
}
