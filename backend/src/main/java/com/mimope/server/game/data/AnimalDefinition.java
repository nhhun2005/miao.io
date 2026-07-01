package com.mimope.server.game.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Immutable definition of an animal type in the Mimope game.
 * <p>
 * IDs must match the frontend {@code AnimalDefinition} identifiers exactly
 * so that protocol messages are consistent across client and server.
 */
public record AnimalDefinition(
        /** Unique identifier — must match frontend */
        String id,
        /** Display name */
        String name,
        /** Evolution tier (1 = starter, higher = stronger) */
        int tier,
        /** Base movement speed (units per second) */
        double speed,
        /** Base collision / display radius */
        double radius,
        /** Maximum health points */
        int maxHealth,
        /** XP required to reach this tier (0 for tier 1) */
        int xpRequired,
        /** Biome where this animal spawns */
        Biome biome,
        /** IDs of animals this animal can eat */
        List<String> canEat
) {

    // -----------------------------------------------------------------------
    // First playable animal set (Phase 2, Tier 1–6, land biome)
    // -----------------------------------------------------------------------

    private static final Map<String, AnimalDefinition> REGISTRY;

    static {
        Map<String, AnimalDefinition> map = new LinkedHashMap<>();

        map.put("mouse", new AnimalDefinition(
                "mouse", "Mouse", 1,
                200, 22, 100, 0,
                Biome.LAND, List.of()));

        map.put("rabbit", new AnimalDefinition(
                "rabbit", "Rabbit", 2,
                190, 28, 150, 50,
                Biome.LAND, List.of("mouse")));

        map.put("pig", new AnimalDefinition(
                "pig", "Pig", 3,
                175, 34, 200, 200,
                Biome.LAND, List.of("mouse", "rabbit")));

        map.put("fox", new AnimalDefinition(
                "fox", "Fox", 4,
                185, 38, 300, 500,
                Biome.LAND, List.of("mouse", "rabbit", "pig")));

        map.put("deer", new AnimalDefinition(
                "deer", "Deer", 5,
                180, 44, 450, 1000,
                Biome.LAND, List.of("mouse", "rabbit", "pig", "fox")));

        map.put("lion", new AnimalDefinition(
                "lion", "Lion", 6,
                170, 50, 600, 2000,
                Biome.LAND, List.of("mouse", "rabbit", "pig", "fox", "deer")));

        REGISTRY = Collections.unmodifiableMap(map);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Return all registered animal definitions (unmodifiable). */
    public static Map<String, AnimalDefinition> all() {
        return REGISTRY;
    }

    /** Look up a definition by id, or {@code null} if not found. */
    public static AnimalDefinition byId(String id) {
        return REGISTRY.get(id);
    }

    /** Return the starter animal (tier 1). */
    public static AnimalDefinition starter() {
        return REGISTRY.get("mouse");
    }

    /**
     * Check whether this animal can eat the given target animal.
     *
     * @param targetId the id of the potential prey
     * @return true if this animal's {@code canEat} list contains the target
     */
    public boolean canEat(String targetId) {
        return canEat.contains(targetId);
    }

    /**
     * Return the list of animals that this animal can evolve into
     * (i.e. animals at tier + 1).
     */
    public List<AnimalDefinition> evolutionOptions() {
        int nextTier = this.tier + 1;
        return REGISTRY.values().stream()
                .filter(a -> a.tier() == nextTier)
                .toList();
    }
}
