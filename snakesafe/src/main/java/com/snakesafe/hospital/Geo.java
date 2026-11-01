package com.snakesafe.hospital;

public final class Geo {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private Geo() {
    }

    /** Straight-line (great circle) distance in kilometres. */
    public static double km(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
