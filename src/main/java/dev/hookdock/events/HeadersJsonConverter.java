package dev.hookdock.events;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores headers as a JSON object of name -> list of values. Kept as text rather
 * than jsonb for now since we never query into it.
 */
@Converter
public class HeadersJsonConverter implements AttributeConverter<Map<String, List<String>>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, List<String>>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, List<String>> headers) {
        try {
            return MAPPER.writeValueAsString(headers == null ? Map.of() : headers);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize headers", e);
        }
    }

    @Override
    public Map<String, List<String>> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not deserialize headers", e);
        }
    }
}
