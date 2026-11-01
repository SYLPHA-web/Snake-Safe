package com.snakesafe.alert;

import java.time.Instant;
import java.util.List;

import com.snakesafe.hospital.HospitalView;

/** What the patient sees. {@code alternatives} is filled only after a decline or no reply. */
public record AlertView(String id, AlertStatus status, PatientType patientType, String patientName,
        Integer patientAge, double lat, double lng, HospitalView hospital, Instant createdAt,
        Instant cancelWindowEndsAt, int cancelSecondsLeft, Instant sentAt, Instant replyDeadline,
        Instant confirmedAt, String confirmedAtLabel, String declineReason, String message,
        List<HospitalView> alternatives, String emergencyNumber, String reminder) {
}
