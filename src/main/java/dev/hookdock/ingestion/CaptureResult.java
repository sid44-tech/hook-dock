package dev.hookdock.ingestion;

import java.util.Optional;

import dev.hookdock.events.CapturedWebhook;
import dev.hookdock.ratelimit.RateLimitStatus;

/**
 * What happened to an inbound request. Only {@link Captured} means something
 * was stored.
 */
public sealed interface CaptureResult {

    /**
     * The event was saved (and a forward queued, if the endpoint has one).
     *
     * @param rateLimit the endpoint's quota after this request; empty when rate
     *                  limiting is turned off
     */
    record Captured(CapturedWebhook event, Optional<RateLimitStatus> rateLimit) implements CaptureResult {
    }

    /** The token doesn't match an enabled endpoint. Nothing was stored. */
    record NotFound() implements CaptureResult {
    }

    /**
     * The endpoint is over its rate limit. Nothing was stored.
     *
     * @param rateLimit the endpoint's quota; {@code resetAfter} is how long
     *                  until it will accept a request again
     */
    record RateLimited(RateLimitStatus rateLimit) implements CaptureResult {
    }
}
