package com.loganalyzer.processor.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

public class JsonSerdeFactory {

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static <T> Serde<T> createSerde(Class<T> targetClass) {
        JsonSerializer<T> serializer = new JsonSerializer<>(mapper);
        
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(targetClass, mapper);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(true);

        return Serdes.serdeFrom(serializer, deserializer);
    }
}
