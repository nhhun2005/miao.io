package com.mimope.server.game;

import com.mimope.server.game.data.Biome;
import com.mimope.server.game.data.AiAnimalDefinition;
import com.mimope.server.game.data.FoodDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service responsible for food spawning, despawning, and lifecycle management.
 * <p>
 * Manages weighted-random food selection by biome, respects the maximum
 * food cap, and handles periodic despawn of stale food items to keep
 * the food distribution fresh.
 */
public class FoodSpawnService {

    private static final Logger log = LoggerFactory.getLogger(FoodSpawnService.class);

    /**
     * Maximum age (in ticks) before a food item is eligible for despawn.
     * At 20 ticks/sec this is ~60 seconds.
     */
    private static final long MAX_FOOD_AGE_TICKS = 1200;

    /**
     * Fraction of stale food removed per tick (to avoid mass despawn spikes).
     */
    private static final double DESPAWN_RATE = 0.05;

    private final double worldWidth;
    private final double worldHeight;
    private final int maxFood;
    private final AtomicLong idCounter;

    /** Tracks when each food instance was spawned (tick number). */
    private final Map<String, Long> foodSpawnTick = new HashMap<>();

    public FoodSpawnService(double worldWidth, double worldHeight, int maxFood, AtomicLong idCounter) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.maxFood = maxFood;
        this.idCounter = idCounter;
    }

    /**
     * Spawn food to fill up to the maximum count.
     *
     * @param currentFoods the current food map (mutated in place)
     * @param currentTick  the current world tick for age tracking
     */
    public void replenishFood(Map<String, FoodEntity> currentFoods, long currentTick) {
        int deficit = maxFood - currentFoods.size();
        if (deficit <= 0) return;

        for (int i = 0; i < deficit; i++) {
            double x = randomRange(0, worldWidth);
            double y = randomRange(0, worldHeight);
            Biome biome = biomeAt(x, y);
            List<FoodDefinition> biomeFoods = FoodDefinition.all().values().stream()
                    .filter(food -> food.biome() == biome)
                    .toList();
            if (biomeFoods.isEmpty()) {
                biomeFoods = FoodDefinition.all().values().stream()
                        .filter(food -> food.biome() == Biome.LAND)
                        .toList();
            }

            FoodDefinition chosen = pickWeightedRandom(biomeFoods, totalWeight(biomeFoods));
            chosen = rollAiFoodVariant(chosen);
            x = Math.max(chosen.radius(), Math.min(worldWidth - chosen.radius(), x));
            y = Math.max(chosen.radius(), Math.min(worldHeight - chosen.radius(), y));

            String instanceId = "f" + idCounter.incrementAndGet();
            FoodEntity food = new FoodEntity(instanceId, chosen, x, y);
            currentFoods.put(instanceId, food);
            foodSpawnTick.put(instanceId, currentTick);
        }
    }

    /**
     * Despawn stale food items that have existed longer than {@link #MAX_FOOD_AGE_TICKS}.
     * Only removes a fraction per tick to avoid sudden mass removal.
     *
     * @param currentFoods the current food map (mutated in place)
     * @param currentTick  the current world tick
     */
    public void despawnStaleFood(Map<String, FoodEntity> currentFoods, long currentTick) {
        List<String> staleIds = new ArrayList<>();

        for (Map.Entry<String, Long> entry : foodSpawnTick.entrySet()) {
            if (currentTick - entry.getValue() > MAX_FOOD_AGE_TICKS) {
                staleIds.add(entry.getKey());
            }
        }

        if (staleIds.isEmpty()) return;

        // Remove only a fraction each tick to spread the respawn load
        int toRemove = Math.max(1, (int) (staleIds.size() * DESPAWN_RATE));
        Collections.shuffle(staleIds);

        for (int i = 0; i < toRemove && i < staleIds.size(); i++) {
            String id = staleIds.get(i);
            currentFoods.remove(id);
            foodSpawnTick.remove(id);
        }
    }

    /**
     * Notify the spawn service that a food item was consumed (removed externally).
     * Cleans up internal tracking state.
     */
    public void onFoodConsumed(String instanceId) {
        foodSpawnTick.remove(instanceId);
    }

    // ------------------------------------------------------------------ helpers

    private static FoodDefinition pickWeightedRandom(List<FoodDefinition> foods, int totalWeight) {
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (FoodDefinition fd : foods) {
            cumulative += fd.spawnWeight();
            if (roll < cumulative) {
                return fd;
            }
        }
        return foods.get(0);
    }

    private int totalWeight(List<FoodDefinition> foods) {
        return foods.stream().mapToInt(FoodDefinition::spawnWeight).sum();
    }

    private FoodDefinition rollAiFoodVariant(FoodDefinition chosen) {
        for (AiAnimalDefinition aiAnimal : AiAnimalDefinition.all().values()) {
            if (chosen.id().equals(aiAnimal.variantOf())
                    && ThreadLocalRandom.current().nextDouble() < aiAnimal.variantRollRate()) {
                FoodDefinition variant = FoodDefinition.byId(aiAnimal.id());
                if (variant != null) {
                    return variant;
                }
            }
        }
        return chosen;
    }

    private Biome biomeAt(double x, double y) {
        if (x < worldWidth * 0.28) {
            return Biome.OCEAN;
        }
        if (y > worldHeight * 0.64) {
            return Biome.ARCTIC;
        }
        return Biome.LAND;
    }

    private static double randomRange(double min, double max) {
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    public int getMaxFood() {
        return maxFood;
    }
}
