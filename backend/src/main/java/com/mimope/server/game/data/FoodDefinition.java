package com.mimope.server.game.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Immutable definition of a food type in the Mimope game.
 * <p>
 * IDs must match the frontend {@code FoodDefinition} identifiers exactly
 * so that protocol messages are consistent across client and server.
 */
public record FoodDefinition(
        /** Unique identifier — must match frontend */
        String id,
        /** Display name */
        String name,
        /** XP awarded when a player picks up this food */
        int xp,
        /** Base collision / display radius */
        double radius,
        /** Minimum animal tier required to eat this food (1 = any) */
        int minTier,
        /** Biome where this food spawns */
        Biome biome,
        /** Spawn weight — higher = more common */
        int spawnWeight
) {

    // -----------------------------------------------------------------------
    // First playable food set (Phase 2, land biome)
    // -----------------------------------------------------------------------

    private static final Map<String, FoodDefinition> REGISTRY;

    static {
        Map<String, FoodDefinition> map = new LinkedHashMap<>();

        map.put("berry", new FoodDefinition(
                "berry", "Berry", 5, 10, 1, Biome.LAND, 50));

        map.put("banana", new FoodDefinition(
                "banana", "Banana", 15, 14, 1, Biome.LAND, 30));

        map.put("meat", new FoodDefinition(
                "meat", "Meat", 50, 16, 3, Biome.LAND, 10));

        map.put("coconut", new FoodDefinition(
                "coconut", "Coconut", 25, 14, 2, Biome.LAND, 20));

        map.put("watermelon", new FoodDefinition(
                "watermelon", "Watermelon", 80, 20, 4, Biome.LAND, 5));

        map.put("seaweed", new FoodDefinition(
                "seaweed", "Seaweed", 20, 14, 1, Biome.OCEAN, 35));

        map.put("arctic_berry", new FoodDefinition(
                "arctic_berry", "Arctic Berry", 12, 10, 1, Biome.ARCTIC, 35));

        map.put("snail", new FoodDefinition(
                "snail", "Snail", 25, 14, 1, Biome.LAND, 8));

        map.put("snail2", new FoodDefinition(
                "snail2", "Snail", 25, 14, 1, Biome.LAND, 0));

        REGISTRY = Collections.unmodifiableMap(map);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Return all registered food definitions (unmodifiable). */
    public static Map<String, FoodDefinition> all() {
        return REGISTRY;
    }

    /** Look up a definition by id, or {@code null} if not found. */
    public static FoodDefinition byId(String id) {
        return REGISTRY.get(id);
    }

    /** Return all food IDs as an unmodifiable list. */
    public static List<String> allIds() {
        return List.copyOf(REGISTRY.keySet());
    }

    /**
     * Return the total spawn weight across all registered foods.
     * Useful for weighted-random food selection.
     */
    public static int totalSpawnWeight() {
        return REGISTRY.values().stream()
                .mapToInt(FoodDefinition::spawnWeight)
                .sum();
    }

    /**
     * Check whether a player with the given animal tier can eat this food.
     *
     * @param animalTier the tier of the player's current animal
     * @return true if the player meets the minimum tier requirement
     */
    public boolean canBeEatenByTier(int animalTier) {
        return animalTier >= this.minTier;
    }
}
