package com.snakesafe.alert;

import java.time.Instant;

/** What hospital staff see: no exact location, no health details. Just who is coming and how far. */
public record StaffAlertView(String id, AlertStatus status, String statusLabel, PatientType patientType,
        String patientName, Integer patientAge, Double distanceKm, Integer etaMinutes, Instant sentAt,
        Instant confirmedAt, Instant arrivedAt, String declineReason) {
}
