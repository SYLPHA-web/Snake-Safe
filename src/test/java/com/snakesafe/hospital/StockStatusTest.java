package com.snakesafe.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class StockStatusTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void updatedWithinTheWindowIsVerified() {
        assertThat(StockStatus.of(5, NOW.minus(Duration.ofMinutes(30)), NOW, 2)).isEqualTo(StockStatus.VERIFIED);
    }

    @Test
    void exactlyAtTheLimitIsStillVerified() {
        assertThat(StockStatus.of(5, NOW.minus(Duration.ofHours(2)), NOW, 2)).isEqualTo(StockStatus.VERIFIED);
    }

    @Test
    void olderThanTheWindowIsUnverified() {
        assertThat(StockStatus.of(5, NOW.minus(Duration.ofHours(2)).minusSeconds(1), NOW, 2))
                .isEqualTo(StockStatus.UNVERIFIED);
        assertThat(StockStatus.of(5, NOW.minus(Duration.ofDays(3)), NOW, 2)).isEqualTo(StockStatus.UNVERIFIED);
    }

    @Test
    void noVialsIsNoneNoMatterHowFreshTheUpdate() {
        assertThat(StockStatus.of(0, NOW, NOW, 2)).isEqualTo(StockStatus.NONE);
        assertThat(StockStatus.of(0, NOW.minus(Duration.ofDays(9)), NOW, 2)).isEqualTo(StockStatus.NONE);
    }

    @Test
    void theWindowFollowsTheConfiguredHours() {
        Instant threeHoursAgo = NOW.minus(Duration.ofHours(3));
        assertThat(StockStatus.of(4, threeHoursAgo, NOW, 2)).isEqualTo(StockStatus.UNVERIFIED);
        assertThat(StockStatus.of(4, threeHoursAgo, NOW, 4)).isEqualTo(StockStatus.VERIFIED);
    }
}
