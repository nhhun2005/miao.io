package com.mimope.server.protocol.inbound;

import com.mimope.server.protocol.ProtocolConstants;
import com.mimope.server.websocket.InboundMessage;

/**
 * Typed DTO for the {@code "grid_debug"} client message.
 *
 * @param enabled whether spatial-grid debug data should be included in snapshots
 */
public record GridDebugMessage(boolean enabled) {

    public static final String TYPE = ProtocolConstants.TYPE_GRID_DEBUG;

    public static GridDebugMessage from(InboundMessage raw) {
        Boolean enabled = raw.getBoolean("enabled");
        if (enabled == null) {
            return null;
        }
        return new GridDebugMessage(enabled);
    }
}
