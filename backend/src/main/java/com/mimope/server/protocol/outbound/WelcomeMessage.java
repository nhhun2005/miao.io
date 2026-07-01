package com.mimope.server.protocol.outbound;

import com.mimope.server.protocol.ProtocolConstants;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server response sent after a successful {@code "join"}.
 *
 * <pre>
 * {
 *   "type": "welcome",
 *   "playerId": "abc-123",
 *   "nickname": "Player1",
 *   "protocolVersion": 1
 * }
 * </pre>
 */
public record WelcomeMessage(
        String playerId,
        String nickname,
        int protocolVersion
) {

    public static final String TYPE = ProtocolConstants.TYPE_WELCOME;

    public WelcomeMessage(String playerId, String nickname) {
        this(playerId, nickname, ProtocolConstants.PROTOCOL_VERSION);
    }

    /** Convert to a field map suitable for {@link com.mimope.server.websocket.MessageEncoder}. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("playerId", playerId);
        map.put("nickname", nickname);
        map.put("protocolVersion", protocolVersion);
        return map;
    }
}
