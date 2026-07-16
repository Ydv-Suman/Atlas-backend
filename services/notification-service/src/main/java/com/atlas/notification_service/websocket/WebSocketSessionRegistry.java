package com.atlas.notification_service.websocket;

import com.atlas.notification_service.dto.WebSocketStatusUpdate;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, WebSocketSession> sessions;
    private final ObjectMapper objectMapper;

    public WebSocketSessionRegistry(ObjectMapper objectMapper) {
        this.sessions = new ConcurrentHashMap<>();
        this.objectMapper = objectMapper;
    }

    public void register(String jobId, WebSocketSession session) {
        sessions.put(jobId, session);
    }

    public void remove(String jobId) {
        sessions.remove(jobId);
    }

    public void sendUpdate(String jobId, WebSocketStatusUpdate update) {
        WebSocketSession session = sessions.get(jobId);
        if (session == null) {
            return;
        }

        if (!session.isOpen()) {
            sessions.remove(jobId);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(update);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Failed to send WebSocket update for jobId={}: {}", jobId, e.getMessage());
            sessions.remove(jobId);
        }
    }

    public boolean isConnected(String jobId) {
        WebSocketSession session = sessions.get(jobId);
        return session != null && session.isOpen();
    }
}
