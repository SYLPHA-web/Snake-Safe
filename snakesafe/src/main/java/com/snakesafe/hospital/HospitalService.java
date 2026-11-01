package com.snakesafe.hospital;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snakesafe.config.AppProperties;
import com.snakesafe.web.ApiException;

@Service
public class HospitalService {

    /** Rough road speed used for the arrival estimate. */
    private static final double SPEED_KMH = 40.0;

    private final HospitalRepository hospitals;
    private final StockRepository stocks;
    private final AppProperties props;
    private final Clock clock;

    public HospitalService(HospitalRepository hospitals, StockRepository stocks, AppProperties props, Clock clock) {
        this.hospitals = hospitals;
        this.stocks = stocks;
        this.props = props;
        this.clock = clock;
    }

    /** All active hospitals, nearest first when a location is given, otherwise by name. */
    @Transactional(readOnly = true)
    public List<HospitalView> list(Double lat, Double lng) {
        boolean located = checkLocation(lat, lng, false);
        List<Hospital> all = hospitals.findByActiveTrue();
        Map<Long, AntivenomStock> stockById = stockMap(all);
        Comparator<HospitalView> order = located
                ? Comparator.comparingDouble(HospitalView::distanceKm)
                : Comparator.comparing(HospitalView::name);
        return all.stream()
                .map(h -> view(h, stockById.get(h.id), located ? lat : null, located ? lng : null))
                .sorted(order)
                .toList();
    }

    @Transactional(readOnly = true)
    public HospitalView get(long id, Double lat, Double lng) {
        boolean located = checkLocation(lat, lng, false);
        Hospital h = hospitals.findById(id).filter(x -> x.active)
                .orElseThrow(() -> ApiException.notFound("HOSPITAL_NOT_FOUND", "Hospital not found."));
        return view(h, stocks.findByHospitalId(id).orElse(null), located ? lat : null, located ? lng : null);
    }

    /** View of any hospital (active or not), used by alerts and staff screens. */
    @Transactional(readOnly = true)
    public HospitalView viewOf(long id, Double lat, Double lng) {
        Hospital h = hospitals.findById(id)
                .orElseThrow(() -> ApiException.notFound("HOSPITAL_NOT_FOUND", "Hospital not found."));
        return view(h, stocks.findByHospitalId(id).orElse(null), lat, lng);
    }

    @Transactional(readOnly = true)
    public BestHospitalResponse best(Double lat, Double lng) {
        checkLocation(lat, lng, true);
        List<HospitalView> nearestFirst = list(lat, lng);
        Optional<HospitalView> best = BestHospitalSelector.pick(nearestFirst);
        List<HospitalView> others = nearestFirst.stream()
                .filter(h -> h.stock().status() != StockStatus.NONE)
                .filter(h -> best.map(b -> !b.id().equals(h.id())).orElse(true))
                .toList();
        if (best.isPresent()) {
            return new BestHospitalResponse(true, best.get(), others,
                    "Nearest hospital with verified antivenom stock.");
        }
        return new BestHospitalResponse(false, null, others,
                "No nearby hospital has verified antivenom stock right now. Call " + props.emergencyNumber()
                        + " now and call a hospital below before you go.");
    }

    /** Other hospitals to try after a decline or no reply: verified stock first, then by distance. */
    @Transactional(readOnly = true)
    public List<HospitalView> alternatives(double lat, double lng, long excludeId) {
        return list(lat, lng).stream()
                .filter(h -> !h.id().equals(excludeId))
                .filter(h -> h.stock().status() != StockStatus.NONE)
                .sorted(Comparator.comparing((HospitalView h) -> h.stock().status() == StockStatus.VERIFIED ? 0 : 1)
                        .thenComparingDouble(HospitalView::distanceKm))
                .toList();
    }

    /** Staff sets the count: updatedAt becomes now, which makes the stock Verified. */
    @Transactional
    public StockInfo updateStock(long hospitalId, int vials) {
        AntivenomStock s = stocks.findByHospitalId(hospitalId).orElseGet(() -> {
            AntivenomStock fresh = new AntivenomStock();
            fresh.hospitalId = hospitalId;
            return fresh;
        });
        s.vials = vials;
        s.updatedAt = clock.instant();
        return stockInfo(stocks.save(s));
    }

    public HospitalView view(Hospital h, AntivenomStock s, Double lat, Double lng) {
        Double km = null;
        Integer eta = null;
        if (lat != null && lng != null) {
            double raw = Geo.km(lat, lng, h.lat, h.lng);
            km = Geo.round1(raw);
            eta = Math.max(1, (int) Math.round(raw / SPEED_KMH * 60.0));
        }
        String maps = String.format(Locale.ROOT, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f", h.lat, h.lng);
        return new HospitalView(h.id, h.name, h.district, h.address, h.phone, h.lat, h.lng, km, eta,
                stockInfo(s), maps);
    }

    public StockInfo stockInfo(AntivenomStock s) {
        if (s == null) {
            return new StockInfo(0, StockStatus.NONE, null, null);
        }
        Instant now = clock.instant();
        long minutes = Math.max(0, Duration.between(s.updatedAt, now).toMinutes());
        return new StockInfo(s.vials, StockStatus.of(s.vials, s.updatedAt, now, props.verifiedHours()),
                s.updatedAt, minutes);
    }

    private Map<Long, AntivenomStock> stockMap(List<Hospital> all) {
        if (all.isEmpty()) {
            return Map.of();
        }
        return stocks.findByHospitalIdIn(all.stream().map(h -> h.id).toList()).stream()
                .collect(Collectors.toMap(s -> s.hospitalId, Function.identity()));
    }

    /** Returns true when a valid location was supplied. */
    private static boolean checkLocation(Double lat, Double lng, boolean required) {
        if (lat == null && lng == null) {
            if (required) {
                throw ApiException.badRequest("LOCATION_OFF",
                        "We could not get your location. Turn on location, or choose a hospital yourself.");
            }
            return false;
        }
        if (lat == null || lng == null) {
            throw ApiException.badRequest("LOCATION_OFF", "Both lat and lng are needed.");
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180 || lat.isNaN() || lng.isNaN()) {
            throw ApiException.badRequest("INVALID_LOCATION", "That location is not valid.");
        }
        return true;
    }
}
