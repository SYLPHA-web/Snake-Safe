package com.snakesafe.alert;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Turns committed alert changes into socket messages for the patient and for the hospital. */
@Component
public class AlertBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(AlertBroadcaster.class);

    private final AlertRepository alerts;
    private final AlertMapper mapper;
    private final RealtimeHub hub;
    private final ObjectMapper json;

    public AlertBroadcaster(AlertRepository alerts, AlertMapper mapper, RealtimeHub hub, ObjectMapper json) {
        this.alerts = alerts;
        this.mapper = mapper;
        this.hub = hub;
        this.json = json;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChange(AlertEvent event) {
        Alert a = alerts.findById(event.alertId()).orElse(null);
        if (a == null) {
            return;
        }
        try {
            hub.send("alert:" + a.id, json.writeValueAsString(Map.of("type", "alert", "alert", mapper.patientView(a))));
            String hospitalType = switch (event.kind()) {
                case SENT_TO_HOSPITAL -> "alert_new";
                case UPDATED -> "alert_updated";
                case CANCELLED_AFTER_SEND -> "alert_cancelled";
                case PATIENT_ONLY -> null;
            };
            if (hospitalType != null) {
                hub.send("hospital:" + a.hospitalId,
                        json.writeValueAsString(Map.of("type", hospitalType, "alert", mapper.staffView(a))));
            }
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Could not push alert update: {}", e.getClass().getSimpleName());
        }
    }

    public String patientJson(String alertId) throws JsonProcessingException {
        Alert a = alerts.findById(alertId).orElse(null);
        return a == null ? null : json.writeValueAsString(Map.of("type", "alert", "alert", mapper.patientView(a)));
    }

    public String hospitalSnapshotJson(long hospitalId, java.util.List<StaffAlertView> current)
            throws JsonProcessingException {
        return json.writeValueAsString(Map.of("type", "snapshot", "alerts", current));
    }
}
