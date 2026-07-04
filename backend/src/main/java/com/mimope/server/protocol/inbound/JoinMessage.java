package com.mimope.server.protocol.inbound;

import com.mimope.server.protocol.ProtocolConstants;
import com.mimope.server.websocket.InboundMessage;

/**
 * Typed DTO for the {@code "join"} client message.
 * <p>
 * Sent when a player wants to enter the game with a chosen nickname.
 *
 * <pre>
 * { "type": "join", "nickname": "Player1" }
 * </pre>
 *
 * @param nickname the requested display name (1–16 alphanumeric chars)
 */
public record JoinMessage(String nickname, String starterAnimalId) {

    public static final String TYPE = ProtocolConstants.TYPE_JOIN;

    /**
     * Parse from a generic {@link InboundMessage}.
     *
     * @return a typed JoinMessage, or {@code null} if required fields are missing
     */
    public static JoinMessage from(InboundMessage raw) {
        String nickname = raw.getString("nickname");
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        String starterAnimalId = raw.getString("starterAnimalId");
        return new JoinMessage(nickname.trim(), starterAnimalId == null ? null : starterAnimalId.trim());
    }
}
