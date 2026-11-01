package com.snakesafe.hospital;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class BestHospitalSelector {

    private BestHospitalSelector() {
    }

    /** The nearest hospital whose stock is VERIFIED. Works on any order: it sorts by distance itself. */
    public static Optional<HospitalView> pick(List<HospitalView> hospitals) {
        return hospitals.stream()
                .filter(h -> h.stock().status() == StockStatus.VERIFIED && h.distanceKm() != null)
                .min(Comparator.comparingDouble(HospitalView::distanceKm));
    }
}
