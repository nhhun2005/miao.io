package com.mimope.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes outbound (server → client) messages as JSON {@link TextMessage}s
 * and sends them over a {@link WebSocketSession}.
 * <p>
 * All outbound messages include a {@code "type"} field plus any extra fields
 * provided in the payload map. Sending is synchronised on the session to
 * avoid concurrent-write issues on a single WebSocket connection.
 */
@Component
public class MessageEncoder {

    private static final Logger log = LoggerFactory.getLogger(MessageEncoder.class);

    private final ObjectMapper objectMapper;

    public MessageEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Build and send a JSON message to a single session.
     *
     * @param session the target session
     * @param type    the message type (e.g. {@code "welcome"}, {@code "pong"}, {@code "error"})
     * @param fields  additional key-value pairs to include in the JSON object
     */
    public void send(ClientSession session, String type, Map<String, Object> fields) {
        WebSocketSession ws = session.getWebSocketSession();
        if (!ws.isOpen()) {
            return;
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        if (fields != null) {
            envelope.putAll(fields);
        }

        try {
            String json = objectMapper.writeValueAsString(envelope);
            synchronized (ws) {
                ws.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("Failed to send '{}' to session {}: {}", type, session.getId(), e.getMessage());
        }
    }

    /**
     * Convenience overload with no extra fields.
     */
    public void send(ClientSession session, String type) {
        send(session, type, null);
    }

    /**
     * Send an error message to a single session.
     */
    public void sendError(ClientSession session, String message) {
        send(session, "error", Map.of("message", message));
    }
}
