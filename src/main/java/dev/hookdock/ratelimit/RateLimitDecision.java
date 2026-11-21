package dev.hookdock.ratelimit;

import java.time.Duration;
import java.util.Optional;

/**
 * Outcome of asking the limiter for a request.
 *
 * @param allowed whether the request may proceed
 * @param status  the endpoint's quota after this request. Empty only when the
 *                limiter is disabled, since there is no quota to report.
 */
public record RateLimitDecision(boolean allowed, Optional<RateLimitStatus> status) {

    private static final RateLimitDecision UNLIMITED = new RateLimitDecision(true, Optional.empty());

    static RateLimitDecision unlimited() {
        return UNLIMITED;
    }

    static RateLimitDecision allowed(RateLimitStatus status) {
        return new RateLimitDecision(true, Optional.of(status));
    }

    static RateLimitDecision rejected(RateLimitStatus status) {
        return new RateLimitDecision(false, Optional.of(status));
    }

    /** How long until a request would be accepted. Zero when this one was. */
    public Duration retryAfter() {
        return allowed ? Duration.ZERO : status.orElseThrow().resetAfter();
    }
}
