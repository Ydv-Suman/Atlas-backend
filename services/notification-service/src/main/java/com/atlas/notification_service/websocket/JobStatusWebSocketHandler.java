package com.atlas.notification_service.websocket;

import com.atlas.shared.security.JwtTokenParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobStatusWebSocketHandler extends TextWebSocketHandler {

    private static final CloseStatus UNAUTHORIZED = new CloseStatus(4001, "Unauthorized");

    private final WebSocketSessionRegistry registry;
    private final JwtTokenParser jwtTokenParser;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null) {
            session.close(UNAUTHORIZED);
            return;
        }

        String jobId = extractJobId(uri.getPath());
        String token = extractToken(uri.getQuery());

        if (token == null || jobId == null) {
            session.close(UNAUTHORIZED);
            return;
        }

        var claims = jwtTokenParser.validateAndExtract(token);
        if (claims.isEmpty()) {
            session.close(UNAUTHORIZED);
            return;
        }

        registry.register(jobId, session);
        log.info("WebSocket connected for job={}, user={}", jobId, claims.get().username());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        URI uri = session.getUri();
        if (uri != null) {
            String jobId = extractJobId(uri.getPath());
            if (jobId != null) {
                registry.remove(jobId);
                log.info("WebSocket disconnected for job={}", jobId);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.debug("Ignoring client message on one-directional WebSocket");
    }

    private String extractJobId(String path) {
        if (path == null) return null;
        // Path: /api/v1/ws/jobs/{jobId}
        String[] segments = path.split("/");
        if (segments.length < 2) return null;
        return segments[segments.length - 1];
    }

    private String extractToken(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
