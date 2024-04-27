package dev.hookdock.ingestion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Replaces values of credential-bearing headers before they are persisted.
 * Provider signature headers are intentionally kept: they are only valid for
 * the exact payload and are needed to debug verification failures.
 */
@Component
public class HeaderRedactor {

    static final String REDACTED = "[REDACTED]";

    private final Set<String> redacted;

    public HeaderRedactor(IngestionProperties properties) {
        this.redacted = properties.redactedHeaders().stream()
                .map(h -> h.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public Map<String, List<String>> redact(Map<String, List<String>> headers) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (redacted.contains(name.toLowerCase(Locale.ROOT))) {
                result.put(name, values.stream().map(v -> REDACTED).toList());
            } else {
                result.put(name, values);
            }
        });
        return result;
    }
}
