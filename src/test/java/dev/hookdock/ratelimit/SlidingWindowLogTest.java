package dev.hookdock.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SlidingWindowLogTest {

    private static final long SECOND = 1_000_000_000L;
    private static final Duration MINUTE = Duration.ofMinutes(1);

    private static long seconds(double s) {
        return (long) (s * SECOND);
    }

    @Test
    void allowsTwentyThenRejectsForTheRestOfTheMinute() {
        SlidingWindowLog window = new SlidingWindowLog(20, MINUTE);
        for (int i = 0; i < 20; i++) {
            assertThat(window.tryAcquire(0).allowed()).as("request %d", i + 1).isTrue();
        }
        RateLimitDecision rejected = window.tryAcquire(0);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isEqualTo(MINUTE);

        // Nothing trickles back in during the minute: no refill, no burst.
        for (int s = 1; s < 60; s++) {
            assertThat(window.tryAcquire(seconds(s)).allowed()).as("at %ds", s).isFalse();
        }
        assertThat(window.tryAcquire(seconds(59.999)).allowed()).isFalse();
    }

    @Test
    void slotsReopenExactlyOneWindowAfterEachAcceptedRequest() {
        SlidingWindowLog window = new SlidingWindowLog(20, MINUTE);
        for (int i = 0; i < 20; i++) {
            window.tryAcquire(0);
        }
        // At 60s all 20 from t=0 have left the window.
        for (int i = 0; i < 20; i++) {
            assertThat(window.tryAcquire(seconds(60)).allowed()).isTrue();
        }
        assertThat(window.tryAcquire(seconds(60)).allowed()).isFalse();
    }

    @Test
    void windowRollsRatherThanResettingOnAFixedBoundary() {
        SlidingWindowLog window = new SlidingWindowLog(20, MINUTE);
        for (int i = 0; i < 10; i++) {
            assertThat(window.tryAcquire(seconds(0)).allowed()).isTrue();
        }
        for (int i = 0; i < 10; i++) {
            assertThat(window.tryAcquire(seconds(30)).allowed()).isTrue();
        }
        assertThat(window.tryAcquire(seconds(59)).allowed()).isFalse();

        // At 60s only the t=0 requests have expired, so exactly 10 more fit.
        for (int i = 0; i < 10; i++) {
            assertThat(window.tryAcquire(seconds(60)).allowed()).isTrue();
        }
        RateLimitDecision rejected = window.tryAcquire(seconds(60));
        assertThat(rejected.allowed()).isFalse();
        // Next slot opens when the t=30 requests leave the window.
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void neverMoreThanTheLimitInAnyRollingMinute() {
        // One request every 0.5s for 5 minutes; count what was accepted in
        // every trailing 60s span.
        SlidingWindowLog window = new SlidingWindowLog(20, MINUTE);
        List<Long> acceptedAt = new ArrayList<>();
        for (long t = 0; t <= seconds(300); t += SECOND / 2) {
            if (window.tryAcquire(t).allowed()) {
                acceptedAt.add(t);
            }
        }
        for (long end = 0; end <= seconds(300); end += SECOND / 2) {
            long windowEnd = end;
            long inWindow = acceptedAt.stream()
                    .filter(a -> a <= windowEnd && windowEnd - a < seconds(60))
                    .count();
            assertThat(inWindow).as("window ending at %dns", end).isLessThanOrEqualTo(20);
        }
        // 20 at the start of each minute (0s, 60s, ... 240s), plus the first
        // request of the sixth minute at exactly 300s.
        assertThat(acceptedAt).hasSize(101);
    }

    @Test
    void retryAfterCountsDownToTheOldestRequestLeavingTheWindow() {
        SlidingWindowLog window = new SlidingWindowLog(2, MINUTE);
        window.tryAcquire(seconds(0));
        window.tryAcquire(seconds(20));
        assertThat(window.tryAcquire(seconds(25)).retryAfter()).isEqualTo(Duration.ofSeconds(35));
        assertThat(window.tryAcquire(seconds(50)).retryAfter()).isEqualTo(Duration.ofSeconds(10));

        // Waiting exactly that long is enough.
        assertThat(window.tryAcquire(seconds(60)).allowed()).isTrue();
        assertThat(window.tryAcquire(seconds(60)).retryAfter()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void reportsLimitAndRemainingAfterEachRequest() {
        SlidingWindowLog window = new SlidingWindowLog(3, MINUTE);
        assertThat(window.tryAcquire(seconds(0)).status())
                .contains(new RateLimitStatus(3, 2, MINUTE));
        assertThat(window.tryAcquire(seconds(10)).status())
                .contains(new RateLimitStatus(3, 1, Duration.ofSeconds(50)));
        assertThat(window.tryAcquire(seconds(20)).status())
                .contains(new RateLimitStatus(3, 0, Duration.ofSeconds(40)));

        RateLimitDecision rejected = window.tryAcquire(seconds(30));
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.status()).contains(new RateLimitStatus(3, 0, Duration.ofSeconds(30)));
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofSeconds(30));

        // The t=0 request has left; the t=10 one is now the oldest.
        assertThat(window.tryAcquire(seconds(60)).status())
                .contains(new RateLimitStatus(3, 0, Duration.ofSeconds(10)));
        // By t=85 the t=10 and t=20 requests have left; only t=60 is still counted.
        assertThat(window.tryAcquire(seconds(85)).status())
                .contains(new RateLimitStatus(3, 1, Duration.ofSeconds(35)));
    }

    @Test
    void retryAfterIsZeroForAnAllowedRequest() {
        SlidingWindowLog window = new SlidingWindowLog(1, MINUTE);
        assertThat(window.tryAcquire(0).retryAfter()).isEqualTo(Duration.ZERO);
    }

    @Test
    void loweringTheLimitKeepsTheEndpointBlockedUntilItIsBelowTheNewLimit() {
        SlidingWindowLog window = new SlidingWindowLog(10, MINUTE);
        for (int s = 0; s < 6; s++) {
            window.tryAcquire(seconds(s));
        }
        // 6 requests at 0..5s, limit lowered to 3.
        SlidingWindowLog lowered = window.withLimit(3, seconds(10));
        assertThat(lowered.limit()).isEqualTo(3);

        RateLimitDecision rejected = lowered.tryAcquire(seconds(10));
        assertThat(rejected.allowed()).isFalse();
        // Below 3 in the window once the 4th request (at 3s) has left, at 63s.
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofSeconds(53));
        assertThat(lowered.tryAcquire(seconds(62.9)).allowed()).isFalse();
        assertThat(lowered.tryAcquire(seconds(63)).allowed()).isTrue();
        assertThat(lowered.tryAcquire(seconds(63)).allowed()).isFalse();
    }

    @Test
    void raisingTheLimitAllowsOnlyTheDifferenceInTheCurrentWindow() {
        SlidingWindowLog window = new SlidingWindowLog(3, MINUTE);
        for (int i = 0; i < 3; i++) {
            window.tryAcquire(0);
        }
        SlidingWindowLog raised = window.withLimit(5, seconds(10));
        assertThat(raised.tryAcquire(seconds(10)).status()).contains(new RateLimitStatus(5, 1, Duration.ofSeconds(50)));
        assertThat(raised.tryAcquire(seconds(10)).allowed()).isTrue();
        assertThat(raised.tryAcquire(seconds(10)).allowed()).isFalse();
    }

    @Test
    void loweringThenRaisingTheLimitDoesNotForgetRecentRequests() {
        SlidingWindowLog window = new SlidingWindowLog(20, MINUTE);
        for (int i = 0; i < 15; i++) {
            window.tryAcquire(0);
        }
        SlidingWindowLog roundTrip = window.withLimit(5, seconds(1)).withLimit(20, seconds(2));

        // Still 15 counted, so only 5 more fit, not a fresh 20.
        for (int i = 0; i < 5; i++) {
            assertThat(roundTrip.tryAcquire(seconds(2)).allowed()).isTrue();
        }
        assertThat(roundTrip.tryAcquire(seconds(2)).allowed()).isFalse();
    }

    @Test
    void changingTheLimitDropsRequestsThatHaveAlreadyLeftTheWindow() {
        SlidingWindowLog window = new SlidingWindowLog(3, MINUTE);
        for (int i = 0; i < 3; i++) {
            window.tryAcquire(0);
        }
        SlidingWindowLog resized = window.withLimit(2, seconds(60));
        assertThat(resized.isIdle(seconds(60))).isTrue();
    }

    @Test
    void rejectedRequestsDoNotExtendTheLockout() {
        SlidingWindowLog window = new SlidingWindowLog(1, MINUTE);
        window.tryAcquire(0);
        for (int s = 1; s < 60; s++) {
            window.tryAcquire(seconds(s));
        }
        assertThat(window.tryAcquire(seconds(60)).allowed()).isTrue();
    }

    @Test
    void isIdleOnceEveryAcceptedRequestHasLeftTheWindow() {
        SlidingWindowLog window = new SlidingWindowLog(20, MINUTE);
        assertThat(window.isIdle(0)).isTrue();
        window.tryAcquire(0);
        window.tryAcquire(seconds(30));
        assertThat(window.isIdle(seconds(59))).isFalse();
        assertThat(window.isIdle(seconds(60))).isFalse();
        assertThat(window.isIdle(seconds(90))).isTrue();
    }

    @Test
    void toleratesClockGoingBackwards() {
        SlidingWindowLog window = new SlidingWindowLog(1, MINUTE);
        window.tryAcquire(seconds(100));
        // Earlier timestamp: still limited, and the wait is never more than one window.
        RateLimitDecision rejected = window.tryAcquire(seconds(90));
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isEqualTo(MINUTE);
        assertThat(window.tryAcquire(seconds(160)).allowed()).isTrue();
    }
}
