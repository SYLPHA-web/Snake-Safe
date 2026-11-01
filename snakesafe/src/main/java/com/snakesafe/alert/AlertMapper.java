package com.snakesafe.alert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import com.snakesafe.config.AppProperties;
import com.snakesafe.hospital.HospitalService;
import com.snakesafe.hospital.HospitalView;

@Component
public class AlertMapper {

    private static final String REMINDER = "Treat every bite as an emergency.";

    private final HospitalService hospitals;
    private final AppProperties props;
    private final Clock clock;
    private final DateTimeFormatter timeFormat;

    public AlertMapper(HospitalService hospitals, AppProperties props, Clock clock) {
        this.hospitals = hospitals;
        this.props = props;
        this.clock = clock;
        this.timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of(props.timezone()));
    }

    public AlertView patientView(Alert a) {
        Instant now = clock.instant();
        HospitalView hospital = hospitals.viewOf(a.hospitalId, a.lat, a.lng);
        int secondsLeft = a.status == AlertStatus.PENDING_CANCEL
                ? (int) Math.max(0, (long) Math.ceil(Duration.between(now, a.cancelWindowEndsAt).toMillis() / 1000.0))
                : 0;
        boolean tryOthers = a.status == AlertStatus.DECLINED || a.status == AlertStatus.NO_REPLY;
        List<HospitalView> alternatives = tryOthers ? hospitals.alternatives(a.lat, a.lng, a.hospitalId) : null;
        Instant deadline = a.sentAt == null ? null : a.sentAt.plus(Duration.ofMinutes(props.noReplyMinutes()));
        String confirmedLabel = a.confirmedAt == null ? null : timeFormat.format(a.confirmedAt);
        return new AlertView(a.id, a.status, a.patientType, a.patientName, a.patientAge, a.lat, a.lng, hospital,
                a.createdAt, a.cancelWindowEndsAt, secondsLeft, a.sentAt,
                a.status == AlertStatus.WAITING ? deadline : null, a.confirmedAt, confirmedLabel, a.declineReason,
                message(a, secondsLeft, confirmedLabel), alternatives, props.emergencyNumber(), REMINDER);
    }

    public StaffAlertView staffView(Alert a) {
        HospitalView h = hospitals.viewOf(a.hospitalId, a.lat, a.lng);
        return new StaffAlertView(a.id, a.status, staffLabel(a.status), a.patientType, a.patientName, a.patientAge,
                h.distanceKm(), h.etaMinutes(), a.sentAt, a.confirmedAt, a.arrivedAt, a.declineReason);
    }

    private String message(Alert a, int secondsLeft, String confirmedLabel) {
        return switch (a.status) {
            case PENDING_CANCEL -> "Sending to the hospital in " + secondsLeft + " seconds. You can cancel now.";
            case WAITING -> "The hospital will confirm";
            case CONFIRMED -> "Antivenom available (confirmed " + confirmedLabel + ")";
            case DECLINED -> "This hospital cannot take you now. Please choose another hospital or call "
                    + props.emergencyNumber() + ".";
            case NO_REPLY -> "No reply from the hospital in " + props.noReplyMinutes()
                    + " minutes. Please choose another hospital or call " + props.emergencyNumber() + ".";
            case CANCELLED -> "Alert cancelled.";
            case ARRIVED -> "You have reached the hospital.";
        };
    }

    private static String staffLabel(AlertStatus status) {
        return switch (status) {
            case PENDING_CANCEL, WAITING -> "Waiting for your reply";
            case CONFIRMED -> "Patient on the way";
            case DECLINED -> "Declined";
            case NO_REPLY -> "No reply";
            case CANCELLED -> "Cancelled by patient";
            case ARRIVED -> "Patient arrived";
        };
    }
}
