package dev.hookdock.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Inbound rate limit, applied per endpoint.
 *
 * @param enabled  turn the limiter off entirely (every request is accepted)
 * @param requests the most requests an endpoint may accept in any rolling
 *                 {@code period}
 * @param period   length of the rolling window
 */
@ConfigurationProperties(prefix = "hookdock.rate-limit")
public record RateLimitProperties(Boolean enabled, Integer requests, Duration period) {

    public static final int DEFAULT_REQUESTS = 20;
    public static final Duration DEFAULT_PERIOD = Duration.ofMinutes(1);

    public RateLimitProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (requests == null) {
            requests = DEFAULT_REQUESTS;
        }
        if (period == null) {
            period = DEFAULT_PERIOD;
        }
        // Fail fast rather than guess: "0 requests" is not a way to disable the
        // limiter, and a zero-length window would never limit anything.
        if (requests <= 0) {
            throw new IllegalArgumentException(
                    "hookdock.rate-limit.requests must be at least 1 (set hookdock.rate-limit.enabled=false to turn the limiter off)");
        }
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("hookdock.rate-limit.period must be a positive duration");
        }
    }
}
