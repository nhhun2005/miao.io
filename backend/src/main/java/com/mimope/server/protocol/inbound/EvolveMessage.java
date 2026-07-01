package com.mimope.server.protocol.inbound;

import com.mimope.server.protocol.ProtocolConstants;
import com.mimope.server.websocket.InboundMessage;

/**
 * Typed DTO for the {@code "evolve"} client message.
 * <p>
 * Sent when the player selects an evolution option from the evolution modal.
 *
 * <pre>
 * { "type": "evolve", "animalId": "rabbit" }
 * </pre>
 *
 * @param animalId the ID of the animal the player wants to evolve into
 */
public record EvolveMessage(String animalId) {

    public static final String TYPE = ProtocolConstants.TYPE_EVOLVE;

    /**
     * Parse from a generic {@link InboundMessage}.
     *
     * @return a typed EvolveMessage, or {@code null} if required fields are missing
     */
    public static EvolveMessage from(InboundMessage raw) {
        String animalId = raw.getString("animalId");
        if (animalId == null || animalId.isBlank()) {
            return null;
        }
        return new EvolveMessage(animalId.trim());
    }
}
