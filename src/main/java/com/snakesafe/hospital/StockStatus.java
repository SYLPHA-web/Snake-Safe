package com.snakesafe.hospital;

import java.time.Duration;
import java.time.Instant;

public enum StockStatus {
    VERIFIED, UNVERIFIED, NONE;

    /** NONE when there are no vials; VERIFIED only if staff updated the count within the last verifiedHours. */
    public static StockStatus of(int vials, Instant updatedAt, Instant now, int verifiedHours) {
        if (vials <= 0) {
            return NONE;
        }
        if (updatedAt == null) {
            return UNVERIFIED;
        }
        return Duration.between(updatedAt, now).compareTo(Duration.ofHours(verifiedHours)) <= 0 ? VERIFIED : UNVERIFIED;
    }
}
