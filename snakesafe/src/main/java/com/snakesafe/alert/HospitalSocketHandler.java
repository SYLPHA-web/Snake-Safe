package com.snakesafe.alert;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.JsonProcessingException;

/** /ws/hospital/{id}?token=JWT: staff get the open alerts on connect, then new, updated and cancelled ones. */
@Component
public class HospitalSocketHandler extends TextWebSocketHandler {

    public static final String HOSPITAL_ATTRIBUTE = "hospitalId";

    private final RealtimeHub hub;
    private final AlertBroadcaster broadcaster;
    private final AlertService alerts;

    public HospitalSocketHandler(RealtimeHub hub, AlertBroadcaster broadcaster, AlertService alerts) {
        this.hub = hub;
        this.broadcaster = broadcaster;
        this.alerts = alerts;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object attr = session.getAttributes().get(HOSPITAL_ATTRIBUTE);
        if (!(attr instanceof Long hospitalId)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        WebSocketSession safe = hub.register("hospital:" + hospitalId, session);
        try {
            hub.sendTo(safe, broadcaster.hospitalSnapshotJson(hospitalId, alerts.staffAlerts(hospitalId)));
        } catch (JsonProcessingException e) {
            // the first live update will bring the screen up to date
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if ("ping".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.unregister(session);
    }
}
