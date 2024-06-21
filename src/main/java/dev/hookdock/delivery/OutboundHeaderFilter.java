package dev.hookdock.delivery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides which captured headers are copied onto an outbound request.
 * <ul>
 * <li>Hop-by-hop and framing headers describe the original connection, not the
 * payload, and the JDK client refuses to set several of them anyway.</li>
 * <li>Credentials are never forwarded, even if they weren't redacted at
 * capture time.</li>
 * <li>Headers we redacted at capture are dropped rather than sent as
 * "[REDACTED]".</li>
 * </ul>
 */
final class OutboundHeaderFilter {

    private static final Set<String> DROPPED = Set.of(
            "host", "content-length", "connection", "keep-alive", "transfer-encoding", "te", "trailer",
            "upgrade", "expect", "proxy-authorization", "proxy-authenticate", "proxy-connection",
            "authorization", "cookie", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto",
            "x-real-ip", "forwarded");

    private OutboundHeaderFilter() {
    }

    static Map<String, List<String>> filter(Map<String, List<String>> captured) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        captured.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (DROPPED.contains(lower) || lower.startsWith("x-hookdock-")) {
                return;
            }
            if (values.stream().anyMatch("[REDACTED]"::equals)) {
                return;
            }
            result.put(lower, values);
        });
        return result;
    }
}
