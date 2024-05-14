package dev.hookdock.ingestion;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "hookdock.ingestion")
public record IngestionProperties(DataSize maxBodySize, List<String> redactedHeaders) {

    public IngestionProperties {
        if (maxBodySize == null) {
            maxBodySize = DataSize.ofMegabytes(1);
        }
        if (redactedHeaders == null) {
            redactedHeaders = List.of();
        }
    }
}
