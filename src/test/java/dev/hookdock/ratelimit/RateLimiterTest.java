package dev.hookdock.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.hookdock.MutableClock;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));

    private RateLimiter limiter(boolean enabled, int requests, Duration period) {
        return new RateLimiter(new RateLimitProperties(enabled, requests, period), clock);
    }

    @Test
    void allowsUpToTheLimitPerRollingWindowThenRejectsWithRetryAfter() {
        RateLimiter limiter = limiter(true, 3, Duration.ofSeconds(30));
        UUID endpoint = UUID.randomUUID();

        assertThat(limiter.tryAcquire(endpoint, null).allowed()).isTrue();
        clock.advance(Duration.ofSeconds(10));
        assertThat(limiter.tryAcquire(endpoint, null).allowed()).isTrue();
        assertThat(limiter.tryAcquire(endpoint, null).allowed()).isTrue();

        RateLimitDecision rejected = limiter.tryAcquire(endpoint, null);
        assertThat(rejected.allowed()).isFalse();
        // The first request (at t=0) leaves the 30s window at t=30.
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofSeconds(20));

        clock.advance(Duration.ofSeconds(19));
        assertThat(limiter.tryAcquire(endpoint, null).allowed()).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire(endpoint, null).allowed()).isTrue();
        assertThat(limiter.tryAcquire(endpoint, null).allowed()).isFalse();
    }

    @Test
    void limitsEachEndpointSeparately() {
        RateLimiter limiter = limiter(true, 1, Duration.ofMinutes(1));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(limiter.tryAcquire(first, null).allowed()).isTrue();
        assertThat(limiter.tryAcquire(first, null).allowed()).isFalse();
        assertThat(limiter.tryAcquire(second, null).allowed()).isTrue();
        assertThat(limiter.tryAcquire(second, null).allowed()).isFalse();
    }

    @Test
    void overrideAppliesOnlyToTheEndpointItIsGivenFor() {
        RateLimiter limiter = limiter(true, 3, Duration.ofMinutes(1));
        UUID overridden = UUID.randomUUID();
        UUID usesDefault = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(overridden, 5).allowed()).isTrue();
        }
        RateLimitDecision rejected = limiter.tryAcquire(overridden, 5);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.status().orElseThrow().limit()).isEqualTo(5);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(usesDefault, null).allowed()).isTrue();
        }
        RateLimitDecision defaultRejected = limiter.tryAcquire(usesDefault, null);
        assertThat(defaultRejected.allowed()).isFalse();
        assertThat(defaultRejected.status().orElseThrow().limit()).isEqualTo(3);
    }

    @Test
    void changingTheLimitKeepsTheRequestsAlreadyInTheWindow() {
        RateLimiter limiter = limiter(true, 20, Duration.ofMinutes(1));
        UUID endpoint = UUID.randomUUID();
        for (int i = 0; i < 8; i++) {
            limiter.tryAcquire(endpoint, null);
        }

        // Lowered below what was already used: blocked straight away, not given a fresh allowance.
        RateLimitDecision lowered = limiter.tryAcquire(endpoint, 5);
        assertThat(lowered.allowed()).isFalse();
        assertThat(lowered.status()).contains(new RateLimitStatus(5, 0, Duration.ofMinutes(1)));

        // Raised: only the difference is available.
        assertThat(limiter.tryAcquire(endpoint, 10).allowed()).isTrue();
        assertThat(limiter.tryAcquire(endpoint, 10).allowed()).isTrue();
        assertThat(limiter.tryAcquire(endpoint, 10).allowed()).isFalse();

        // Back to the default of 20: 10 used, 10 left.
        RateLimitDecision back = limiter.tryAcquire(endpoint, null);
        assertThat(back.allowed()).isTrue();
        assertThat(back.status().orElseThrow().remaining()).isEqualTo(9);
    }

    @Test
    void disabledLimiterAllowsEverythingAndKeepsNoState() {
        RateLimiter limiter = limiter(false, 1, Duration.ofMinutes(1));
        UUID endpoint = UUID.randomUUID();
        for (int i = 0; i < 50; i++) {
            RateLimitDecision decision = limiter.tryAcquire(endpoint, null);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.status()).isEmpty();
        }
        assertThat(limiter.trackedEndpoints()).isZero();
    }

    @Test
    void evictsEndpointsWithNoRequestsLeftInTheWindow() {
        RateLimiter limiter = limiter(true, 2, Duration.ofMinutes(1));
        UUID early = UUID.randomUUID();
        UUID late = UUID.randomUUID();

        limiter.tryAcquire(early, null);
        clock.advance(Duration.ofSeconds(30));
        limiter.tryAcquire(late, null);
        limiter.tryAcquire(late, null);
        assertThat(limiter.trackedEndpoints()).isEqualTo(2);

        // At t=60 the early request has left its window; the late ones haven't.
        clock.advance(Duration.ofSeconds(30));
        limiter.evictIdle();
        assertThat(limiter.trackedEndpoints()).isEqualTo(1);

        // Eviction must not change what the remaining endpoint is allowed.
        assertThat(limiter.tryAcquire(late, null).allowed()).isFalse();

        clock.advance(Duration.ofSeconds(30));
        limiter.evictIdle();
        assertThat(limiter.trackedEndpoints()).isZero();
    }

    @Test
    void propertiesFillInDefaults() {
        RateLimitProperties defaults = new RateLimitProperties(null, null, null);
        assertThat(defaults.enabled()).isTrue();
        assertThat(defaults.requests()).isEqualTo(20);
        assertThat(defaults.period()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void propertiesRejectNonsenseValues() {
        assertThatThrownBy(() -> new RateLimitProperties(true, 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requests");
        assertThatThrownBy(() -> new RateLimitProperties(true, 5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period");
    }
}
