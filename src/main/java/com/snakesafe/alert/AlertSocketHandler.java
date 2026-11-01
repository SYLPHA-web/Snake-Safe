package com.snakesafe.alert;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.JsonProcessingException;

/** /ws/alerts/{id}: the patient gets the current state on connect, then every change. */
@Component
public class AlertSocketHandler extends TextWebSocketHandler {

    private final RealtimeHub hub;
    private final AlertBroadcaster broadcaster;

    public AlertSocketHandler(RealtimeHub hub, AlertBroadcaster broadcaster) {
        this.hub = hub;
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String id = lastSegment(session);
        String current;
        try {
            current = broadcaster.patientJson(id);
        } catch (JsonProcessingException e) {
            current = null;
        }
        if (current == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Alert not found"));
            return;
        }
        WebSocketSession safe = hub.register("alert:" + id, session);
        hub.sendTo(safe, current);
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

    static String lastSegment(WebSocketSession session) {
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
