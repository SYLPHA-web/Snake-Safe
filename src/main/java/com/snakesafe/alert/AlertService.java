package com.snakesafe.alert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.snakesafe.config.AppProperties;
import com.snakesafe.config.Sanitizer;
import com.snakesafe.hospital.HospitalService;
import com.snakesafe.hospital.HospitalView;
import com.snakesafe.hospital.StockStatus;
import com.snakesafe.web.ApiException;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alerts;
    private final HospitalService hospitals;
    private final AlertMapper mapper;
    private final AppProperties props;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public AlertService(AlertRepository alerts, HospitalService hospitals, AlertMapper mapper, AppProperties props,
            Clock clock, ApplicationEventPublisher events) {
        this.alerts = alerts;
        this.hospitals = hospitals;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
        this.events = events;
    }

    // ---- patient side ----------------------------------------------------------------------

    @Transactional
    public AlertView create(AlertRequest req, Long userId) {
        HospitalView hospital = hospitals.viewOf(req.hospitalId(), req.lat(), req.lng());
        if (hospital.stock().status() == StockStatus.NONE) {
            throw ApiException.conflict("NO_STOCK", "This hospital has no antivenom stock. Please choose another hospital.");
        }
        Instant now = clock.instant();
        Alert a = new Alert();
        a.id = UUID.randomUUID().toString();
        a.userId = userId;
        a.patientType = req.patientType();
        a.patientName = Sanitizer.cleanOrNull(req.patientName());
        a.patientAge = req.patientAge();
        a.lat = req.lat();
        a.lng = req.lng();
        a.hospitalId = req.hospitalId();
        a.status = AlertStatus.PENDING_CANCEL;
        a.createdAt = now;
        a.cancelWindowEndsAt = now.plusSeconds(props.cancelWindowSeconds());
        alerts.save(a);
        publish(a, AlertEvent.Kind.PATIENT_ONLY);
        return mapper.patientView(a);
    }

    @Transactional(readOnly = true)
    public AlertView get(String id) {
        return mapper.patientView(load(id));
    }

    /** Allowed inside the cancel window and until the hospital confirms. Cancelling twice is harmless. */
    @Transactional
    public AlertView cancel(String id) {
        Alert a = load(id);
        if (a.status == AlertStatus.CANCELLED) {
            return mapper.patientView(a);
        }
        if (a.status != AlertStatus.PENDING_CANCEL && a.status != AlertStatus.WAITING) {
            throw ApiException.conflict("INVALID_STATE", "This alert can no longer be cancelled.");
        }
        boolean hospitalKnew = a.sentAt != null;
        a.status = AlertStatus.CANCELLED;
        a.cancelledAt = clock.instant();
        alerts.save(a);
        publish(a, hospitalKnew ? AlertEvent.Kind.CANCELLED_AFTER_SEND : AlertEvent.Kind.PATIENT_ONLY);
        return mapper.patientView(a);
    }

    @Transactional
    public AlertView arrived(String id) {
        Alert a = load(id);
        if (a.status == AlertStatus.ARRIVED) {
            return mapper.patientView(a);
        }
        if (a.status != AlertStatus.CONFIRMED && a.status != AlertStatus.WAITING) {
            throw ApiException.conflict("INVALID_STATE", "This alert cannot be marked as arrived.");
        }
        a.status = AlertStatus.ARRIVED;
        a.arrivedAt = clock.instant();
        alerts.save(a);
        publish(a, AlertEvent.Kind.UPDATED);
        return mapper.patientView(a);
    }

    // ---- scheduler -------------------------------------------------------------------------

    /** PENDING_CANCEL -> WAITING once the cancel window is over; WAITING -> NO_REPLY after the timeout. */
    @Transactional
    public void processTimeouts() {
        Instant now = clock.instant();
        for (Alert a : alerts.findByStatusAndCancelWindowEndsAtLessThanEqual(AlertStatus.PENDING_CANCEL, now)) {
            a.status = AlertStatus.WAITING;
            a.sentAt = now;
            alerts.save(a);
            publish(a, AlertEvent.Kind.SENT_TO_HOSPITAL);
            log.info("Alert {} sent to hospital {}", a.id, a.hospitalId);
        }
        Instant lateBefore = now.minus(Duration.ofMinutes(props.noReplyMinutes()));
        for (Alert a : alerts.findByStatusAndSentAtLessThanEqual(AlertStatus.WAITING, lateBefore)) {
            a.status = AlertStatus.NO_REPLY;
            a.respondedAt = now;
            alerts.save(a);
            publish(a, AlertEvent.Kind.UPDATED);
            log.info("Alert {} got no reply from hospital {}", a.id, a.hospitalId);
        }
    }

    // ---- hospital staff side ---------------------------------------------------------------

    /** Alerts the hospital knows about (sent to it) in the last 24 hours, newest first. */
    @Transactional(readOnly = true)
    public List<StaffAlertView> staffAlerts(long hospitalId) {
        Instant since = clock.instant().minus(Duration.ofHours(24));
        return alerts.findByHospitalIdAndSentAtAfterOrderByCreatedAtDesc(hospitalId, since).stream()
                .map(mapper::staffView).toList();
    }

    @Transactional
    public StaffAlertView staffAccept(long hospitalId, String id) {
        Alert a = loadForHospital(hospitalId, id);
        requireStatus(a, AlertStatus.WAITING, "This alert is no longer waiting for a reply.");
        Instant now = clock.instant();
        a.status = AlertStatus.CONFIRMED;
        a.confirmedAt = now;
        a.respondedAt = now;
        alerts.save(a);
        publish(a, AlertEvent.Kind.UPDATED);
        return mapper.staffView(a);
    }

    @Transactional
    public StaffAlertView staffDecline(long hospitalId, String id, String reason) {
        Alert a = loadForHospital(hospitalId, id);
        requireStatus(a, AlertStatus.WAITING, "This alert is no longer waiting for a reply.");
        a.status = AlertStatus.DECLINED;
        a.respondedAt = clock.instant();
        a.declineReason = Sanitizer.cleanOrNull(reason);
        alerts.save(a);
        publish(a, AlertEvent.Kind.UPDATED);
        return mapper.staffView(a);
    }

    @Transactional
    public StaffAlertView staffArrived(long hospitalId, String id) {
        Alert a = loadForHospital(hospitalId, id);
        requireStatus(a, AlertStatus.CONFIRMED, "Only a confirmed alert can be marked as arrived.");
        a.status = AlertStatus.ARRIVED;
        a.arrivedAt = clock.instant();
        alerts.save(a);
        publish(a, AlertEvent.Kind.UPDATED);
        return mapper.staffView(a);
    }

    @Transactional(readOnly = true)
    public long countWithStatus(long hospitalId, AlertStatus status) {
        return alerts.countByHospitalIdAndStatus(hospitalId, status);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private Alert load(String id) {
        return alerts.findById(id).orElseThrow(() -> ApiException.notFound("ALERT_NOT_FOUND", "Alert not found."));
    }

    /** Staff only ever see alerts that were sent to their own hospital. */
    private Alert loadForHospital(long hospitalId, String id) {
        return alerts.findById(id)
                .filter(a -> a.hospitalId == hospitalId && a.sentAt != null)
                .orElseThrow(() -> ApiException.notFound("ALERT_NOT_FOUND", "Alert not found."));
    }

    private static void requireStatus(Alert a, AlertStatus expected, String message) {
        if (a.status != expected) {
            throw ApiException.conflict("INVALID_STATE", message);
        }
    }

    private void publish(Alert a, AlertEvent.Kind kind) {
        events.publishEvent(new AlertEvent(a.id, a.hospitalId, kind));
    }
}
