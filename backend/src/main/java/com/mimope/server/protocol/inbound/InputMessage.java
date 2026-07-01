package com.mimope.server.protocol.inbound;

import com.mimope.server.protocol.ProtocolConstants;
import com.mimope.server.websocket.InboundMessage;

/**
 * Typed DTO for the {@code "input"} client message.
 * <p>
 * Sent at a throttled rate (~20 Hz) with the player's current input state.
 *
 * <pre>
 * {
 *   "type": "input",
 *   "seq": 42,
 *   "angle": 1.5708,
 *   "intensity": 0.85,
 *   "boost": false,
 *   "ability": false,
 *   "timestamp": 1719000000000
 * }
 * </pre>
 *
 * @param seq       monotonically increasing sequence number
 * @param angle     movement angle in radians (0 = right, π/2 = down)
 * @param intensity movement intensity 0–1 (normalised distance from center)
 * @param boost     whether the player is boosting
 * @param ability   whether the player triggered their ability this frame
 * @param timestamp client-side timestamp in ms (for RTT estimation)
 */
public record InputMessage(
        int seq,
        double angle,
        double intensity,
        boolean boost,
        boolean ability,
        long timestamp
) {

    public static final String TYPE = ProtocolConstants.TYPE_INPUT;

    /**
     * Parse from a generic {@link InboundMessage}.
     *
     * @return a typed InputMessage, or {@code null} if required fields are missing
     */
    public static InputMessage from(InboundMessage raw) {
        Number seqNum = raw.getNumber("seq");
        Number angleNum = raw.getNumber("angle");
        if (seqNum == null || angleNum == null) {
            return null;
        }

        Number intensityNum = raw.getNumber("intensity");
        Number timestampNum = raw.getNumber("timestamp");

        // Boolean fields: Jackson deserialises them as Boolean objects in the Map
        Object boostObj = raw.payload().get("boost");
        Object abilityObj = raw.payload().get("ability");

        return new InputMessage(
                seqNum.intValue(),
                angleNum.doubleValue(),
                intensityNum != null ? intensityNum.doubleValue() : 1.0,
                boostObj instanceof Boolean b ? b : false,
                abilityObj instanceof Boolean a ? a : false,
                timestampNum != null ? timestampNum.longValue() : 0L
        );
    }
}
