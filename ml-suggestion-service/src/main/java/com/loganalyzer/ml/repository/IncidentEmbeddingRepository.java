package com.loganalyzer.ml.repository;

import com.loganalyzer.ml.model.IncidentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentEmbeddingRepository extends JpaRepository<IncidentEmbedding, Long> {

    Optional<IncidentEmbedding> findByIncidentId(String incidentId);

    List<IncidentEmbedding> findAll();

    @Query(value = """
        SELECT * FROM incident_embeddings
        WHERE 1 - (embedding <=> CAST(:embedding AS vector)) >= :threshold
        ORDER BY embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<IncidentEmbedding> findSimilarByEmbedding(
            @Param("embedding") float[] embedding,
            @Param("threshold") double threshold,
            @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM incident_embeddings
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<IncidentEmbedding> findTopKSimilar(
            @Param("embedding") float[] embedding,
            @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM incident_embeddings
        WHERE :servicePattern = ANY(service_patterns)
        ORDER BY embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<IncidentEmbedding> findSimilarByServicePattern(
            @Param("embedding") float[] embedding,
            @Param("servicePattern") String servicePattern,
            @Param("limit") int limit);
}