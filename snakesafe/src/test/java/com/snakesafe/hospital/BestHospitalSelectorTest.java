package com.snakesafe.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class BestHospitalSelectorTest {

    private static HospitalView hospital(long id, double km, StockStatus status) {
        return new HospitalView(id, "Sample Hospital " + id, "District", "Address", "080", 0, 0, km, (int) km,
                new StockInfo(status == StockStatus.NONE ? 0 : 5, status, null, 10L), "https://example.invalid");
    }

    @Test
    void picksTheNearestVerifiedHospital() {
        List<HospitalView> list = List.of(
                hospital(1, 3, StockStatus.UNVERIFIED),
                hospital(2, 9, StockStatus.VERIFIED),
                hospital(3, 20, StockStatus.VERIFIED));
        assertThat(BestHospitalSelector.pick(list)).map(HospitalView::id).contains(2L);
    }

    @Test
    void doesNotDependOnTheOrderOfTheList() {
        List<HospitalView> list = List.of(
                hospital(3, 20, StockStatus.VERIFIED),
                hospital(2, 9, StockStatus.VERIFIED),
                hospital(1, 3, StockStatus.NONE));
        assertThat(BestHospitalSelector.pick(list)).map(HospitalView::id).contains(2L);
    }

    @Test
    void skipsNearerHospitalsWithoutStock() {
        List<HospitalView> list = List.of(hospital(1, 1, StockStatus.NONE), hospital(2, 50, StockStatus.VERIFIED));
        assertThat(BestHospitalSelector.pick(list)).map(HospitalView::id).contains(2L);
    }

    @Test
    void returnsNothingWhenNoStockIsVerified() {
        List<HospitalView> list = List.of(hospital(1, 1, StockStatus.UNVERIFIED), hospital(2, 5, StockStatus.NONE));
        assertThat(BestHospitalSelector.pick(list)).isEqualTo(Optional.empty());
    }

    @Test
    void returnsNothingForAnEmptyList() {
        assertThat(BestHospitalSelector.pick(List.of())).isEmpty();
    }
}
