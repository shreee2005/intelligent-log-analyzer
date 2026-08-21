package com.loganalyzer.search.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "logs")
public class LogDocument {
    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Field(type = FieldType.Keyword)
    private String serviceId;

    @Field(type = FieldType.Keyword)
    private String level;

    @Field(type = FieldType.Long)
    private Long projectId;

    // The raw log message optimized for full text search
    @Field(type = FieldType.Text, analyzer = "standard")
    private String message;

    @Field(type = FieldType.Date)
    private Instant timestamp;

    @Field(type = FieldType.Keyword)
    private String traceId;

    @Field(type = FieldType.Keyword)
    private String spanId;
}
