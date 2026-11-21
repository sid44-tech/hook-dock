package dev.hookdock.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.hookdock.MutableClock;

class RecentRejectionsTest {

    private final Instant start = Instant.parse("2024-01-01T00:00:00Z");
    private final MutableClock clock = new MutableClock(start);
    private final RecentRejections log = new RecentRejections(clock);

    private Rejection rejection(UUID endpoint, int secondsAfterStart) {
        return new Rejection(endpoint, start.plusSeconds(secondsAfterStart), "POST", RejectionReason.RATE_LIMITED, 20);
    }

    @Test
    void returnsNewestFirstAcrossEndpoints() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        log.record(rejection(a, 1));
        log.record(rejection(b, 2));
        log.record(rejection(a, 3));

        List<Rejection> recent = log.recentForEndpoints(List.of(a, b), 10);

        assertThat(recent).extracting(r -> r.rejectedAt().getEpochSecond() - start.getEpochSecond())
                .containsExactly(3L, 2L, 1L);
    }

    @Test
    void onlyReturnsTheRequestedEndpoints() {
        UUID mine = UUID.randomUUID();
        UUID someoneElses = UUID.randomUUID();
        log.record(rejection(mine, 1));
        log.record(rejection(someoneElses, 2));

        assertThat(log.recentForEndpoints(List.of(mine), 10)).extracting(Rejection::endpointId).containsExactly(mine);
        assertThat(log.recentForEndpoints(List.of(), 10)).isEmpty();
        assertThat(log.recentForEndpoints(Set.of(UUID.randomUUID()), 10)).isEmpty();
    }

    @Test
    void keepsOnlyTheNewestRejectionsPerEndpoint() {
        UUID flooded = UUID.randomUUID();
        for (int i = 0; i < 1000; i++) {
            log.record(rejection(flooded, i));
        }

        List<Rejection> recent = log.recentForEndpoints(List.of(flooded), 1000);

        assertThat(recent).hasSize(RecentRejections.PER_ENDPOINT);
        assertThat(recent.get(0).rejectedAt()).isEqualTo(start.plusSeconds(999));
        assertThat(recent.get(RecentRejections.PER_ENDPOINT - 1).rejectedAt())
                .isEqualTo(start.plusSeconds(1000 - RecentRejections.PER_ENDPOINT));
    }

    @Test
    void floodingOneEndpointDoesNotEvictAnotherEndpointsRejections() {
        UUID quiet = UUID.randomUUID();
        UUID flooded = UUID.randomUUID();
        log.record(rejection(quiet, 0));
        for (int i = 1; i <= 500; i++) {
            log.record(rejection(flooded, i));
        }

        assertThat(log.recentForEndpoints(List.of(quiet), 10)).hasSize(1);
    }

    @Test
    void appliesTheLimit() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        for (int i = 0; i < 8; i++) {
            log.record(rejection(a, i));
            log.record(rejection(b, 100 + i));
        }

        List<Rejection> recent = log.recentForEndpoints(List.of(a, b), 10);

        assertThat(recent).hasSize(10);
        // All 8 from b (newer) come first, then the newest 2 from a.
        assertThat(recent.subList(0, 8)).allMatch(r -> r.endpointId().equals(b));
        assertThat(recent.subList(8, 10)).allMatch(r -> r.endpointId().equals(a));
    }

    // Rejections stamped with the clock's current time, as CaptureService does.
    private void rejectNow(UUID endpoint, int times) {
        for (int i = 0; i < times; i++) {
            log.record(new Rejection(endpoint, clock.instant(), "POST", RejectionReason.RATE_LIMITED, 20));
        }
    }

    @Test
    void countsEveryRejectionNotJustTheOnesListed() {
        UUID flooded = UUID.randomUUID();
        rejectNow(flooded, 1000);

        assertThat(log.countInWindow(List.of(flooded))).isEqualTo(1000);
        assertThat(log.recentForEndpoints(List.of(flooded), 100)).hasSize(RecentRejections.PER_ENDPOINT);
    }

    @Test
    void countsAcrossTheGivenEndpointsOnly() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID someoneElses = UUID.randomUUID();
        rejectNow(a, 3);
        rejectNow(b, 4);
        rejectNow(someoneElses, 50);

        assertThat(log.countInWindow(List.of(a, b))).isEqualTo(7);
        assertThat(log.countInWindow(List.of(a))).isEqualTo(3);
        assertThat(log.countInWindow(List.of())).isZero();
        assertThat(log.countInWindow(List.of(UUID.randomUUID()))).isZero();
    }

    @Test
    void onlyCountsTheLast24Hours() {
        UUID endpoint = UUID.randomUUID();
        rejectNow(endpoint, 5);                      // t = 0
        clock.advance(Duration.ofHours(12));
        rejectNow(endpoint, 2);                      // t = 12h
        clock.advance(Duration.ofHours(11).plusMinutes(59));
        assertThat(log.countInWindow(List.of(endpoint))).isEqualTo(7);   // t = 23h59m

        clock.advance(Duration.ofMinutes(1));
        assertThat(log.countInWindow(List.of(endpoint))).isEqualTo(2);   // t = 24h: the first 5 are out

        clock.advance(Duration.ofHours(12));
        assertThat(log.countInWindow(List.of(endpoint))).isZero();       // t = 36h
    }

    @Test
    void theListStillShowsRejectionsOlderThanTheCountWindow() {
        UUID endpoint = UUID.randomUUID();
        rejectNow(endpoint, 1);
        clock.advance(Duration.ofDays(2));

        assertThat(log.countInWindow(List.of(endpoint))).isZero();
        assertThat(log.recentForEndpoints(List.of(endpoint), 10)).hasSize(1);
    }

    @Test
    void keepsCountingCorrectlyThroughASteadyFlood() {
        // One rejection a minute for two days: the count settles at one day's worth.
        UUID endpoint = UUID.randomUUID();
        for (int minute = 0; minute < 2 * 24 * 60; minute++) {
            rejectNow(endpoint, 1);
            clock.advance(Duration.ofMinutes(1));
        }
        assertThat(log.countInWindow(List.of(endpoint))).isEqualTo(24 * 60 - 1);
    }

    @Test
    void toleratesTheClockSteppingBackwards() {
        UUID endpoint = UUID.randomUUID();
        rejectNow(endpoint, 2);
        clock.advance(Duration.ofMinutes(-5));
        rejectNow(endpoint, 1);
        assertThat(log.countInWindow(List.of(endpoint))).isEqualTo(3);
    }
}
