package dev.hookdock.ingestion;

import java.util.List;
import java.util.Map;

/**
 * Best-effort guess at who sent a webhook, based on well-known headers.
 * Only used for labelling in the UI.
 */
final class ProviderDetector {

    record ProviderInfo(String provider, String eventId) {
    }

    private ProviderDetector() {
    }

    static ProviderInfo detect(Map<String, List<String>> headers) {
        if (has(headers, "x-github-event")) {
            return new ProviderInfo("github", first(headers, "x-github-delivery"));
        }
        if (has(headers, "stripe-signature")) {
            return new ProviderInfo("stripe", null);
        }
        if (has(headers, "x-shopify-topic")) {
            return new ProviderInfo("shopify", first(headers, "x-shopify-webhook-id"));
        }
        if (has(headers, "x-slack-signature")) {
            return new ProviderInfo("slack", null);
        }
        if (has(headers, "webhook-id")) {
            return new ProviderInfo(null, first(headers, "webhook-id"));
        }
        return new ProviderInfo(null, null);
    }

    private static boolean has(Map<String, List<String>> headers, String name) {
        return headers.containsKey(name);
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
