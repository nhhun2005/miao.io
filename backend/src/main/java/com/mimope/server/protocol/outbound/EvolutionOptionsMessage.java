package com.mimope.server.protocol.outbound;

import com.mimope.server.protocol.ProtocolConstants;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sent to a player when they reach an XP threshold and can evolve.
 *
 * <pre>
 * {
 *   "type": "evolution_options",
 *   "options": [
 *     { "animalId": "rabbit", "name": "Rabbit", "tier": 2 },
 *     { "animalId": "pig",    "name": "Pig",    "tier": 3 }
 *   ]
 * }
 * </pre>
 */
public record EvolutionOptionsMessage(
        List<EvolutionOption> options
) {

    public static final String TYPE = ProtocolConstants.TYPE_EVOLUTION_OPTIONS;

    /** A single evolution option. */
    public record EvolutionOption(
            String animalId,
            String name,
            int tier
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("animalId", animalId);
            m.put("name", name);
            m.put("tier", tier);
            return m;
        }
    }

    /** Convert to a field map suitable for {@link com.mimope.server.websocket.MessageEncoder}. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("options", options.stream().map(EvolutionOption::toMap).toList());
        return map;
    }
}
