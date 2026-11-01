package com.snakesafe.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.snakesafe.MutableClock;
import com.snakesafe.SnakeSafeTest;
import com.snakesafe.web.ApiException;

@SnakeSafeTest
class HospitalServiceTest {

    // Sample data: Bengaluru has fresh stock, Mangaluru has stock checked 5 hours ago, Belagavi has none
    private static final double BENGALURU_LAT = 12.98, BENGALURU_LNG = 77.60;
    private static final double MANGALURU_LAT = 12.91, MANGALURU_LNG = 74.85;

    @Autowired
    HospitalService service;
    @Autowired
    StockRepository stocks;
    @Autowired
    MutableClock clock;

    private Instant startedAt;

    @BeforeEach
    void rememberTime() {
        startedAt = clock.instant();
    }

    @AfterEach
    void restoreTime() {
        clock.setTo(startedAt);
    }

    @Test
    void listIsNearestFirstWithDistanceAndEta() {
        var list = service.list(BENGALURU_LAT, BENGALURU_LNG);
        assertThat(list).hasSize(8);
        assertThat(list.get(0).name()).isEqualTo("Sample Hospital Bengaluru");
        assertThat(list).isSortedAccordingTo(java.util.Comparator.comparingDouble(HospitalView::distanceKm));
        assertThat(list.get(0).etaMinutes()).isPositive();
    }

    @Test
    void bestHospitalIsTheNearestWithVerifiedStock() {
        var best = service.best(BENGALURU_LAT, BENGALURU_LNG);
        assertThat(best.found()).isTrue();
        assertThat(best.hospital().name()).isEqualTo("Sample Hospital Bengaluru");
        assertThat(best.hospital().stock().status()).isEqualTo(StockStatus.VERIFIED);
    }

    @Test
    void nearestHospitalWithOldStockIsSkippedForTheNearestVerifiedOne() {
        var best = service.best(MANGALURU_LAT, MANGALURU_LNG);
        assertThat(best.found()).isTrue();
        assertThat(best.hospital().name()).isNotEqualTo("Sample Hospital Mangaluru");
        // Mangaluru is still offered as another option, flagged unverified
        assertThat(best.others()).anySatisfy(h -> {
            assertThat(h.name()).isEqualTo("Sample Hospital Mangaluru");
            assertThat(h.stock().status()).isEqualTo(StockStatus.UNVERIFIED);
        });
        // hospitals without any stock are not offered at all
        assertThat(best.others()).noneMatch(h -> h.stock().status() == StockStatus.NONE);
    }

    @Test
    void whenNothingIsVerifiedWeSayFoundFalseAndListTheOtherOptions() {
        clock.advance(Duration.ofHours(3));
        var best = service.best(BENGALURU_LAT, BENGALURU_LNG);
        assertThat(best.found()).isFalse();
        assertThat(best.hospital()).isNull();
        assertThat(best.others()).isNotEmpty().allMatch(h -> h.stock().status() == StockStatus.UNVERIFIED);
    }

    @Test
    void missingLocationGivesLocationOff() {
        assertThatThrownBy(() -> service.best(null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("LOCATION_OFF");
                    assertThat(e.getStatus().value()).isEqualTo(400);
                });
    }

    @Test
    void staffUpdateMakesStockVerifiedAgain() {
        var old = service.list(null, null).stream().filter(h -> h.name().endsWith("Kalaburagi")).findFirst().orElseThrow();
        assertThat(old.stock().status()).isEqualTo(StockStatus.UNVERIFIED);
        AntivenomStock before = stocks.findByHospitalId(old.id()).orElseThrow();
        int vials = before.vials;
        Instant updatedAt = before.updatedAt;
        try {
            service.updateStock(old.id(), 9);
            var fresh = service.get(old.id(), null, null);
            assertThat(fresh.stock().status()).isEqualTo(StockStatus.VERIFIED);
            assertThat(fresh.stock().vials()).isEqualTo(9);
        } finally {
            AntivenomStock row = stocks.findByHospitalId(old.id()).orElseThrow();
            row.vials = vials;
            row.updatedAt = updatedAt;
            stocks.save(row);
        }
    }
}
