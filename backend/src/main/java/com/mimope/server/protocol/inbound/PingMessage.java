package com.mimope.server.protocol.inbound;

import com.mimope.server.protocol.ProtocolConstants;
import com.mimope.server.websocket.InboundMessage;

/**
 * Typed DTO for the {@code "ping"} client message.
 * <p>
 * Sent periodically by the client for latency measurement.
 * The server echoes the timestamp back in a {@code "pong"} response.
 *
 * <pre>
 * { "type": "ping", "timestamp": 1719000000000 }
 * </pre>
 *
 * @param timestamp client-side timestamp in ms, or 0 if not provided
 */
public record PingMessage(long timestamp) {

    public static final String TYPE = ProtocolConstants.TYPE_PING;

    /**
     * Parse from a generic {@link InboundMessage}.
     *
     * @return a typed PingMessage (always succeeds since timestamp is optional)
     */
    public static PingMessage from(InboundMessage raw) {
        Number ts = raw.getNumber("timestamp");
        return new PingMessage(ts != null ? ts.longValue() : 0L);
    }
}
