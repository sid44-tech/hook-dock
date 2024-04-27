package dev.hookdock.ingestion;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hookdock.ingestion")
public record IngestionProperties(List<String> redactedHeaders) {

    public IngestionProperties {
        if (redactedHeaders == null) {
            redactedHeaders = List.of();
        }
    }
}
