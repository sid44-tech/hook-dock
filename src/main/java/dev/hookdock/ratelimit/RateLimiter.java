package dev.hookdock.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-endpoint inbound rate limiter: each endpoint may accept at most its
 * limit (its own override, or the server-wide {@code requests} default) in any
 * rolling {@code period}.
 * <p>
 * State lives in memory only, so it starts fresh on every restart and is not
 * shared between instances. That is fine for a single HookDock instance. Keys
 * are endpoint IDs, so memory is bounded by the number of endpoints; windows
 * with no recent requests are dropped by a periodic sweep.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<UUID, SlidingWindowLog> windows = new ConcurrentHashMap<>();

    public RateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Records a request for the endpoint if it is under its limit for the
     * current rolling window. Always allows when the limiter is disabled, and
     * keeps no state in that case.
     *
     * @param limitOverride the endpoint's own limit, or null to use the
     *                      server default. If it differs from the limit used
     *                      last time, the endpoint's recent requests are kept
     *                      and measured against the new limit.
     */
    public RateLimitDecision tryAcquire(UUID endpointId, Integer limitOverride) {
        if (!properties.enabled()) {
            return RateLimitDecision.unlimited();
        }
        int limit = limitOverride != null ? limitOverride : properties.requests();
        long now = nowNanos();
        RateLimitDecision[] decision = new RateLimitDecision[1];
        // compute() holds the per-key lock, so a window is never updated by two
        // threads at once, and evictIdle() can't remove it between the lookup
        // and the update.
        windows.compute(endpointId, (id, window) -> {
            if (window == null) {
                window = new SlidingWindowLog(limit, properties.period());
            } else if (window.limit() != limit) {
                window = window.withLimit(limit, now);
            }
            decision[0] = window.tryAcquire(now);
            return window;
        });
        if (!decision[0].allowed()) {
            log.debug("Endpoint {} rate limited, next request allowed in {}", endpointId, decision[0].retryAfter());
        }
        return decision[0];
    }

    /**
     * Drops windows with no requests left in them. A missing window and an
     * empty one behave identically, so this only frees memory; it never
     * changes a decision. Runs on a schedule, and can be called directly in
     * tests.
     */
    @Scheduled(fixedDelay = 60_000)
    public void evictIdle() {
        long now = nowNanos();
        for (UUID id : windows.keySet()) {
            windows.computeIfPresent(id, (key, window) -> window.isIdle(now) ? null : window);
        }
    }

    /** Number of endpoints currently holding limiter state. Exposed for tests. */
    int trackedEndpoints() {
        return windows.size();
    }

    private long nowNanos() {
        Instant now = clock.instant();
        return now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }
}
