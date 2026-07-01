package com.mimope.server.protocol.outbound;

import com.mimope.server.protocol.ProtocolConstants;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server response to a client {@code "ping"}.
 *
 * <pre>
 * { "type": "pong", "timestamp": 1719000000000 }
 * </pre>
 */
public record PongMessage(long timestamp) {

    public static final String TYPE = ProtocolConstants.TYPE_PONG;

    /** Convert to a field map suitable for {@link com.mimope.server.websocket.MessageEncoder}. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (timestamp > 0) {
            map.put("timestamp", timestamp);
        }
        return map;
    }
}
