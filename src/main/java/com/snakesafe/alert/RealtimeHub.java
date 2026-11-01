package com.snakesafe.alert;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** Keeps track of open sockets per topic ("alert:ID" or "hospital:ID") and pushes text to them. */
@Component
public class RealtimeHub {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);

    private final Map<String, Map<String, WebSocketSession>> topics = new ConcurrentHashMap<>();

    public WebSocketSession register(String topic, WebSocketSession raw) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(raw, 5_000, 256 * 1024);
        topics.computeIfAbsent(topic, t -> new ConcurrentHashMap<>()).put(raw.getId(), safe);
        return safe;
    }

    public void unregister(WebSocketSession raw) {
        topics.values().forEach(sessions -> sessions.remove(raw.getId()));
        topics.values().removeIf(Map::isEmpty);
    }

    public void send(String topic, String json) {
        Map<String, WebSocketSession> sessions = topics.get(topic);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession s : sessions.values()) {
            sendTo(s, json);
        }
    }

    public void sendTo(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Dropping a socket that could not be written to");
            unregister(session);
        }
    }
}
