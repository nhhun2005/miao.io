package com.mimope.server.websocket;

import java.util.Map;

/**
 * Represents a decoded inbound (client → server) WebSocket message.
 * <p>
 * All client messages are JSON objects with at least a {@code "type"} field.
 * The remaining payload fields are kept in a generic {@link Map} until
 * the protocol DTOs are defined in Phase 7.
 */
public record InboundMessage(
        /** Message type, e.g. {@code "join"}, {@code "input"}, {@code "ping"}. */
        String type,
        /** Raw payload fields (everything except {@code "type"}). */
        Map<String, Object> payload
) {

    /**
     * Convenience accessor for a string payload field.
     *
     * @return the value cast to String, or {@code null} if absent or wrong type
     */
    public String getString(String key) {
        Object v = payload.get(key);
        return v instanceof String s ? s : null;
    }

    /**
     * Convenience accessor for a numeric payload field.
     *
     * @return the value as a {@link Number}, or {@code null} if absent or wrong type
     */
    public Number getNumber(String key) {
        Object v = payload.get(key);
        return v instanceof Number n ? n : null;
    }

    /**
     * Convenience accessor for a boolean payload field.
     *
     * @return the value cast to Boolean, or {@code null} if absent or wrong type
     */
    public Boolean getBoolean(String key) {
        Object v = payload.get(key);
        return v instanceof Boolean b ? b : null;
    }
}
