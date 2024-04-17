package dev.hookdock.ingestion;

import java.util.List;
import java.util.Map;

public record InboundRequest(
        String method,
        String path,
        String queryString,
        Map<String, List<String>> headers,
        byte[] body,
        String contentType) {
}
