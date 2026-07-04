package com.mimope.server.game.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable definition of a playable animal type in the Mimope game.
 */
public record AnimalDefinition(
        String id,
        String name,
        int tier,
        double speed,
        double radius,
        int maxHealth,
        int xpRequired,
        Biome biome,
        String abilityId,
        boolean normalEvolution
) {

    private static final int FINAL_UNLOCK_XP = 3_000_000;
    private static final Map<String, AnimalDefinition> REGISTRY;

    static {
        Map<String, AnimalDefinition> map = new LinkedHashMap<>();

        add(map, "mouse", "Mouse", 1, 200, 22, 100, 0, Biome.LAND, "dash", true);
        add(map, "shrimp", "Shrimp", 1, 205, 20, 95, 0, Biome.OCEAN, "dash", true);
        add(map, "chipmunk", "Chipmunk", 1, 198, 21, 100, 0, Biome.ARCTIC, "dash", true);
        add(map, "lemming", "Lemming", 1, 190, 19, 80, 0, Biome.ARCTIC, "dash", true);

        add(map, "rabbit", "Rabbit", 2, 190, 28, 150, 50, Biome.LAND, "burrow_dash", true);
        add(map, "arctichare", "Arctic Hare", 2, 188, 27, 150, 50, Biome.ARCTIC, "burrow_dash", true);
        add(map, "trout", "Trout", 2, 196, 26, 140, 50, Biome.OCEAN, "dash", true);

        add(map, "mole", "Mole", 3, 178, 32, 190, 200, Biome.LAND, "dig_dash", true);
        add(map, "crab", "Crab", 3, 170, 34, 220, 200, Biome.OCEAN, "shell_guard", true);
        add(map, "penguin", "Penguin", 3, 182, 31, 185, 200, Biome.ARCTIC, "ice_slide", true);

        add(map, "pig", "Pig", 4, 175, 34, 200, 500, Biome.LAND, "stink_dash", true);
        add(map, "seahorse", "Seahorse", 4, 185, 33, 205, 500, Biome.OCEAN, "dash", true);
        add(map, "seal", "Seal", 4, 180, 35, 220, 500, Biome.ARCTIC, "ice_slide", true);

        add(map, "deer", "Deer", 5, 180, 44, 450, 1_000, Biome.LAND, "forage_dash", true);
        add(map, "squid", "Squid", 5, 174, 40, 360, 1_000, Biome.OCEAN, "ink_dash", true);
        add(map, "reindeer", "Reindeer", 5, 176, 42, 430, 1_000, Biome.ARCTIC, "dash", true);

        add(map, "fox", "Fox", 6, 185, 38, 300, 2_000, Biome.LAND, "dash", true);
        add(map, "jellyfish", "Jellyfish", 6, 165, 42, 380, 2_000, Biome.OCEAN, "sting_pulse", true);
        add(map, "arcticfox", "Arctic Fox", 6, 182, 38, 320, 2_000, Biome.ARCTIC, "dash", true);

        add(map, "zebra", "Zebra", 7, 176, 46, 520, 4_000, Biome.LAND, "back_kick", true);
        add(map, "donkey", "Donkey", 7, 172, 46, 560, 4_000, Biome.LAND, "back_kick", true);
        add(map, "turtle", "Turtle", 7, 158, 48, 700, 4_000, Biome.OCEAN, "shell_guard", true);
        add(map, "muskox", "Musk Ox", 7, 168, 48, 620, 4_000, Biome.ARCTIC, "charge", true);

        add(map, "cheetah", "Cheetah", 8, 205, 48, 580, 8_000, Biome.LAND, "dash", true);
        add(map, "stingray", "Stingray", 8, 176, 50, 620, 8_000, Biome.OCEAN, "shock_pulse", true);
        add(map, "wolf", "Wolf", 8, 186, 49, 600, 8_000, Biome.ARCTIC, "dash", true);

        add(map, "gorilla", "Gorilla", 9, 166, 54, 760, 16_000, Biome.LAND, "claw", true);
        add(map, "pufferfish", "Pufferfish", 9, 160, 52, 760, 16_000, Biome.OCEAN, "inflate_guard", true);
        add(map, "snowleopard", "Snow Leopard", 9, 195, 52, 690, 16_000, Biome.ARCTIC, "dash", true);

        add(map, "bear", "Bear", 10, 160, 58, 900, 32_000, Biome.LAND, "claw", true);
        add(map, "swordfish", "Swordfish", 10, 188, 56, 820, 32_000, Biome.OCEAN, "charge", true);
        add(map, "walrus", "Walrus", 10, 154, 60, 980, 32_000, Biome.ARCTIC, "shell_guard", true);

        add(map, "lion", "Lion", 11, 170, 60, 950, 64_000, Biome.LAND, "roar_pulse", true);
        add(map, "croc", "Crocodile", 11, 158, 62, 1_100, 64_000, Biome.LAND, "croc_bite", true);
        add(map, "octopus", "Octopus", 11, 162, 62, 980, 64_000, Biome.OCEAN, "ink_dash", true);
        add(map, "polarbear", "Polar Bear", 11, 158, 63, 1_120, 64_000, Biome.ARCTIC, "claw", true);

        add(map, "rhino", "Rhino", 12, 166, 66, 1_250, 125_000, Biome.LAND, "charge", true);
        add(map, "shark", "Shark", 12, 184, 64, 1_180, 125_000, Biome.OCEAN, "charge", true);
        add(map, "wolverine", "Wolverine", 12, 174, 62, 1_120, 125_000, Biome.ARCTIC, "claw", true);

        add(map, "hippo", "Hippo", 13, 152, 72, 1_550, 250_000, Biome.LAND, "roar_pulse", true);
        add(map, "killerwhale", "Killer Whale", 13, 176, 74, 1_450, 250_000, Biome.OCEAN, "wave_pulse", true);

        add(map, "mammoth", "Mammoth", 14, 145, 82, 1_900, 500_000, Biome.ARCTIC, "snowball_dash", true);

        add(map, "dragon", "Dragon", 15, 162, 88, 2_300, 1_000_000, Biome.LAND, "fire_dash", true);
        add(map, "kraken", "Kraken", 15, 150, 90, 2_400, 1_000_000, Biome.OCEAN, "whirlpool_pulse", true);
        add(map, "yeti", "Yeti", 15, 154, 88, 2_350, 1_000_000, Biome.ARCTIC, "freeze_pulse", true);

        add(map, "blackdragon", "Black Dragon", 17, 156, 105, 3_500, FINAL_UNLOCK_XP, Biome.FINAL, "fire_dash", false);

        REGISTRY = Collections.unmodifiableMap(map);
    }

    private static void add(Map<String, AnimalDefinition> map,
                            String id,
                            String name,
                            int tier,
                            double speed,
                            double radius,
                            int maxHealth,
                            int xpRequired,
                            Biome biome,
                            String abilityId,
                            boolean normalEvolution) {
        map.put(id, new AnimalDefinition(
                id, name, tier, speed, radius, maxHealth, xpRequired, biome, abilityId, normalEvolution));
    }

    public static Map<String, AnimalDefinition> all() {
        return REGISTRY;
    }

    public static AnimalDefinition byId(String id) {
        return REGISTRY.get(id);
    }

    public static AnimalDefinition starter() {
        return REGISTRY.get("mouse");
    }

    public static boolean isValidStarter(String id) {
        return id == null || id.equals("mouse") || id.equals("shrimp") || id.equals("chipmunk");
    }

    public boolean canEat(AnimalDefinition target) {
        if (target == null || id.equals(target.id())) {
            return false;
        }
        int tierDelta = tier - target.tier();
        return tierDelta >= 1 && tierDelta <= 6;
    }

    public boolean canEat(String targetId) {
        return canEat(byId(targetId));
    }

    public List<AnimalDefinition> evolutionOptions() {
        int nextTier = this.tier + 1;
        List<AnimalDefinition> nextTierOptions = REGISTRY.values().stream()
                .filter(AnimalDefinition::normalEvolution)
                .filter(a -> a.tier() == nextTier)
                .toList();
        if (!nextTierOptions.isEmpty()) {
            return nextTierOptions;
        }

        int nextAvailableTier = REGISTRY.values().stream()
                .filter(AnimalDefinition::normalEvolution)
                .mapToInt(AnimalDefinition::tier)
                .filter(t -> t > this.tier)
                .min()
                .orElse(-1);
        if (nextAvailableTier < 0) {
            return List.of();
        }
        return REGISTRY.values().stream()
                .filter(AnimalDefinition::normalEvolution)
                .filter(a -> a.tier() == nextAvailableTier)
                .toList();
    }

    public boolean canUnlockFinal(double xp) {
        return (id.equals("dragon") || id.equals("kraken") || id.equals("yeti")) && xp >= FINAL_UNLOCK_XP;
    }
}
