package com.mimope.server.game.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AnimalVariantDefinition(
        String id,
        String baseAnimalId,
        double rollRate
) {
    private static final Map<String, AnimalVariantDefinition> REGISTRY;

    static {
        Map<String, AnimalVariantDefinition> map = new LinkedHashMap<>();
        add(map, "crab2", "crab", 0.10);
        add(map, "turtle2", "turtle", 0.10);
        add(map, "muskox2", "muskox", 0.10);
        add(map, "pufferfish2", "pufferfish", 0.10);
        add(map, "swordfish2", "swordfish", 0.10);
        REGISTRY = Collections.unmodifiableMap(map);
    }

    private static void add(Map<String, AnimalVariantDefinition> map, String id, String baseAnimalId, double rollRate) {
        map.put(id, new AnimalVariantDefinition(id, baseAnimalId, rollRate));
    }

    public static Map<String, AnimalVariantDefinition> all() {
        return REGISTRY;
    }
}
