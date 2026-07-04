package com.mimope.server.game.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AiAnimalDefinition(
        String id,
        int spawnWeight,
        String variantOf,
        double variantRollRate
) {
    private static final Map<String, AiAnimalDefinition> REGISTRY;

    static {
        Map<String, AiAnimalDefinition> map = new LinkedHashMap<>();
        map.put("snail", new AiAnimalDefinition("snail", 8, null, 0.0));
        map.put("snail2", new AiAnimalDefinition("snail2", 0, "snail", 0.10));
        REGISTRY = Collections.unmodifiableMap(map);
    }

    public static Map<String, AiAnimalDefinition> all() {
        return REGISTRY;
    }
}
