package dev.hookdock.projects;

import java.time.Duration;

import dev.hookdock.endpoints.WebhookEndpoint;
import dev.hookdock.ratelimit.RateLimitProperties;

/**
 * What the pages need to show rate limits: the per-endpoint field on the
 * project page and the limit column in the dashboard's recent rejections.
 *
 * @param enabled         whether the server enforces rate limits at all
 * @param defaultRequests the server default, shown when an endpoint has no override
 * @param max             the highest override allowed
 * @param periodLabel     the window, for the "/ min" suffix
 */
public record RateLimitView(boolean enabled, int defaultRequests, int max, String periodLabel) {

    public static RateLimitView from(RateLimitProperties properties) {
        return new RateLimitView(properties.enabled(), properties.requests(), WebhookEndpoint.MAX_RATE_LIMIT,
                label(properties.period()));
    }

    // "min" for the default one-minute window; "30 s", "5 min", "hour" and so on otherwise.
    static String label(Duration period) {
        if (period.toMillis() % 1000 != 0) {
            return period.toMillis() + " ms";
        }
        long seconds = period.toSeconds();
        if (seconds % 3600 == 0) {
            return seconds == 3600 ? "hour" : seconds / 3600 + " hours";
        }
        if (seconds % 60 == 0) {
            return seconds == 60 ? "min" : seconds / 60 + " min";
        }
        return seconds == 1 ? "s" : seconds + " s";
    }
}
