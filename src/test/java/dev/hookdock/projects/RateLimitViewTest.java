package dev.hookdock.projects;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RateLimitViewTest {

    @Test
    void labelsTheWindowForTheFieldSuffix() {
        assertThat(RateLimitView.label(Duration.ofMinutes(1))).isEqualTo("min");
        assertThat(RateLimitView.label(Duration.ofMinutes(5))).isEqualTo("5 min");
        assertThat(RateLimitView.label(Duration.ofSeconds(30))).isEqualTo("30 s");
        assertThat(RateLimitView.label(Duration.ofSeconds(1))).isEqualTo("s");
        assertThat(RateLimitView.label(Duration.ofHours(1))).isEqualTo("hour");
        assertThat(RateLimitView.label(Duration.ofHours(2))).isEqualTo("2 hours");
        assertThat(RateLimitView.label(Duration.ofSeconds(90))).isEqualTo("90 s");
        assertThat(RateLimitView.label(Duration.ofMillis(1500))).isEqualTo("1500 ms");
    }
}
