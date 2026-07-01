package com.mimope.server.protocol.outbound;

import com.mimope.server.protocol.ProtocolConstants;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sent to a player when they die.
 *
 * <pre>
 * {
 *   "type": "death",
 *   "reason": "eaten",
 *   "killerNickname": "ProPlayer",
 *   "xpEarned": 450,
 *   "survivalTimeMs": 120000
 * }
 * </pre>
 */
public record DeathMessage(
        String reason,
        String killerNickname,
        double xpEarned,
        long survivalTimeMs
) {

    public static final String TYPE = ProtocolConstants.TYPE_DEATH;

    /** Convert to a field map suitable for {@link com.mimope.server.websocket.MessageEncoder}. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reason", reason);
        if (killerNickname != null) {
            map.put("killerNickname", killerNickname);
        }
        map.put("xpEarned", xpEarned);
        map.put("survivalTimeMs", survivalTimeMs);
        return map;
    }
}
