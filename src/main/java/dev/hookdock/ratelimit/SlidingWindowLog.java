package dev.hookdock.ratelimit;

import java.time.Duration;

/**
 * Rolling-window limit: at most {@code limit} accepted requests in any
 * {@code window}-long span of time. There is no burst allowance on top of
 * that, and no fixed window boundary that resets the count.
 * <p>
 * Keeps the timestamps of recently accepted requests in a ring buffer, so
 * memory per endpoint is fixed and small. A request is allowed when fewer than
 * {@code limit} of those timestamps fall inside the window ending now.
 * Rejected requests are not recorded, so a client that keeps retrying doesn't
 * extend its own lockout.
 * <p>
 * Normally the buffer holds exactly {@code limit} timestamps. After the limit
 * is lowered it can briefly hold more, so that nothing still inside the window
 * is forgotten if the limit is raised again.
 * <p>
 * Not thread-safe on its own: {@link RateLimiter} only touches a window inside
 * a per-key {@code ConcurrentHashMap.compute}, which serialises access.
 */
final class SlidingWindowLog {

    private final int limit;
    private final long windowNanos;
    private final long[] accepted;
    private int head; // index of the oldest recorded timestamp
    private int size;

    SlidingWindowLog(int limit, Duration window) {
        this(limit, window.toNanos(), limit);
    }

    private SlidingWindowLog(int limit, long windowNanos, int capacity) {
        this.limit = limit;
        this.windowNanos = windowNanos;
        this.accepted = new long[capacity];
    }

    RateLimitDecision tryAcquire(long nowNanos) {
        expire(nowNanos);
        if (size < limit) {
            // size < limit <= capacity, so there is always room.
            accepted[(head + size) % accepted.length] = nowNanos;
            size++;
            return RateLimitDecision.allowed(status(nowNanos));
        }
        return RateLimitDecision.rejected(status(nowNanos));
    }

    int limit() {
        return limit;
    }

    /**
     * A copy of this window with a different limit and the same recent
     * requests, so a changed limit applies to the current window straight
     * away: lowering it below what was already used blocks the endpoint until
     * enough requests have left the window, and raising it frees up only the
     * difference.
     */
    SlidingWindowLog withLimit(int newLimit, long nowNanos) {
        expire(nowNanos);
        SlidingWindowLog resized = new SlidingWindowLog(newLimit, windowNanos, Math.max(newLimit, size));
        for (int i = 0; i < size; i++) {
            resized.accepted[i] = accepted[(head + i) % accepted.length];
        }
        resized.size = size;
        return resized;
    }

    /** True when no accepted request is still inside the window. */
    boolean isIdle(long nowNanos) {
        expire(nowNanos);
        return size == 0;
    }

    /*
     * Only called after a request was recorded or rejected, so the window is
     * never empty here. Reset is when the next slot opens, i.e. when the
     * window drops to limit - 1 requests: normally that's when the oldest
     * request leaves, but after the limit was lowered several may have to
     * leave first. Clamped so a wall clock that stepped backwards can't
     * produce a wait longer than one window.
     */
    private RateLimitStatus status(long nowNanos) {
        int next = size >= limit ? size - limit : 0;
        long resetNanos = accepted[(head + next) % accepted.length] + windowNanos - nowNanos;
        resetNanos = Math.max(0, Math.min(windowNanos, resetNanos));
        return new RateLimitStatus(limit, Math.max(0, limit - size), Duration.ofNanos(resetNanos));
    }

    /*
     * A request at time t is inside the window ending at `now` while
     * now - t < window. At exactly one window later it has dropped out, which
     * is the moment the Retry-After we report runs out.
     */
    private void expire(long nowNanos) {
        while (size > 0 && nowNanos - accepted[head] >= windowNanos) {
            head = (head + 1) % accepted.length;
            size--;
        }
    }
}
