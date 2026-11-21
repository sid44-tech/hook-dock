package dev.hookdock.ratelimit;

import java.time.Duration;

/**
 * Snapshot of an endpoint's quota right after a request was checked.
 *
 * @param limit      the most requests accepted in any rolling window
 * @param remaining  how many more requests would be accepted right now
 * @param resetAfter how long until the oldest request still counted leaves the
 *                   window, i.e. until {@code remaining} next goes up. For a
 *                   rejected request this is also how long to wait before
 *                   retrying.
 */
public record RateLimitStatus(int limit, int remaining, Duration resetAfter) {
}
