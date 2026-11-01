package com.snakesafe.hospital;

public record HospitalView(Long id, String name, String district, String address, String phone,
        double lat, double lng, Double distanceKm, Integer etaMinutes, StockInfo stock, String mapsUrl) {
}
