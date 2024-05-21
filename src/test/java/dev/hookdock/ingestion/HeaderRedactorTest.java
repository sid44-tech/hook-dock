package dev.hookdock.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HeaderRedactorTest {

    private final HeaderRedactor redactor = new HeaderRedactor(
            new IngestionProperties(null, List.of("Authorization", "cookie")));

    @Test
    void redactsConfiguredHeadersCaseInsensitively() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("authorization", List.of("Bearer secret"));
        headers.put("Cookie", List.of("a=1", "b=2"));
        headers.put("x-hub-signature-256", List.of("sha256=abc"));

        Map<String, List<String>> result = redactor.redact(headers);

        assertThat(result.get("authorization")).containsExactly(HeaderRedactor.REDACTED);
        assertThat(result.get("Cookie")).containsExactly(HeaderRedactor.REDACTED, HeaderRedactor.REDACTED);
        assertThat(result.get("x-hub-signature-256")).containsExactly("sha256=abc");
    }
}
