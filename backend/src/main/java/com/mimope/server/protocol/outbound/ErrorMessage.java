package com.mimope.server.protocol.outbound;

import com.mimope.server.protocol.ProtocolConstants;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic error message sent to the client.
 *
 * <pre>
 * { "type": "error", "message": "Invalid nickname." }
 * </pre>
 */
public record ErrorMessage(String message) {

    public static final String TYPE = ProtocolConstants.TYPE_ERROR;

    /** Convert to a field map suitable for {@link com.mimope.server.websocket.MessageEncoder}. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message", message);
        return map;
    }
}
