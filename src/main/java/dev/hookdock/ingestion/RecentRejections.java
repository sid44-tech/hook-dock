package dev.hookdock.ingestion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Rejected requests per endpoint, for the dashboard: the most recent few to
 * list, and how many there were in the last 24 hours.
 * <p>
 * Kept in memory on purpose: the point of rejecting a request is to spare the
 * database, so writing a row for every rejection would defeat it. Like the
 * rate limiter's own state, this starts empty on every restart.
 * <p>
 * Memory per endpoint is bounded however hard it is flooded: the list keeps
 * the newest {@link #PER_ENDPOINT}, and the 24 hour total is kept as one
 * counter per minute that had rejections (at most 1,440), dropped once it is
 * older than the window. Keys are real endpoint IDs, so the number of entries
 * is bounded too.
 */
@Component
public class RecentRejections {

    /** Enough to fill the dashboard list from a single endpoint. */
    public static final int PER_ENDPOINT = 10;

    /** How far back {@link #countInWindow} looks. */
    public static final Duration COUNT_WINDOW = Duration.ofHours(24);

    private static final long WINDOW_MINUTES = COUNT_WINDOW.toMinutes();

    private final Map<UUID, EndpointRejections> byEndpoint = new ConcurrentHashMap<>();
    private final Clock clock;

    public RecentRejections(Clock clock) {
        this.clock = clock;
    }

    public void record(Rejection rejection) {
        long oldestMinute = minute(clock.instant()) - WINDOW_MINUTES + 1;
        byEndpoint.compute(rejection.endpointId(), (id, state) -> {
            if (state == null) {
                state = new EndpointRejections();
            }
            state.add(rejection, minute(rejection.rejectedAt()));
            state.dropCountsBefore(oldestMinute);
            return state;
        });
    }

    /**
     * Newest first, across the given endpoints only. Callers pass the signed-in
     * user's endpoints, which is what keeps other users' rejections out.
     */
    public List<Rejection> recentForEndpoints(Collection<UUID> endpointIds, int limit) {
        List<Rejection> all = new ArrayList<>();
        for (UUID id : endpointIds) {
            // computeIfPresent holds the same per-key lock as record(), so the
            // copy never sees a half-updated entry.
            byEndpoint.computeIfPresent(id, (key, state) -> {
                all.addAll(state.recent);
                return state;
            });
        }
        return all.stream()
                .sorted(Comparator.comparing(Rejection::rejectedAt).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Total rejections in the last {@link #COUNT_WINDOW} across the given
     * endpoints, accurate to the minute: the oldest minute counted is the one
     * that started just under 24 hours ago.
     */
    public long countInWindow(Collection<UUID> endpointIds) {
        long oldestMinute = minute(clock.instant()) - WINDOW_MINUTES + 1;
        long[] total = new long[1];
        for (UUID id : endpointIds) {
            byEndpoint.computeIfPresent(id, (key, state) -> {
                state.dropCountsBefore(oldestMinute);
                total[0] += state.countedTotal;
                return state;
            });
        }
        return total[0];
    }

    private static long minute(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), 60);
    }

    /** Not thread-safe; only touched inside the map's per-key compute. */
    private static final class EndpointRejections {

        private final Deque<Rejection> recent = new ArrayDeque<>(PER_ENDPOINT);
        // Oldest first. [0] = minute since the epoch, [1] = rejections in that minute.
        private final Deque<long[]> perMinute = new ArrayDeque<>();
        private long countedTotal;

        void add(Rejection rejection, long minute) {
            if (recent.size() == PER_ENDPOINT) {
                recent.removeLast();
            }
            recent.addFirst(rejection);

            long[] last = perMinute.peekLast();
            if (last != null && last[0] == minute) {
                last[1]++;
            } else if (last == null || minute > last[0]) {
                perMinute.addLast(new long[] { minute, 1 });
            } else {
                // Clock stepped backwards: count it in the newest bucket rather
                // than break the oldest-first order.
                last[1]++;
            }
            countedTotal++;
        }

        void dropCountsBefore(long oldestMinute) {
            while (!perMinute.isEmpty() && perMinute.peekFirst()[0] < oldestMinute) {
                countedTotal -= perMinute.removeFirst()[1];
            }
        }
    }
}
