package com.mimope.server.game;

import com.mimope.server.game.data.Biome;
import com.mimope.server.game.data.FoodDefinition;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service responsible for food spawning and lifecycle management.
 * <p>
 * Manages weighted-random food selection by biome and respects the maximum
 * food cap. Spawned food remains in the world until it is consumed.
 */
public class FoodSpawnService {

    private final double worldWidth;
    private final double worldHeight;
    private final int maxFood;
    private final AtomicLong idCounter;

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
     */
    public void replenishFood(Map<String, FoodEntity> currentFoods) {
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
            x = Math.max(chosen.radius(), Math.min(worldWidth - chosen.radius(), x));
            y = Math.max(chosen.radius(), Math.min(worldHeight - chosen.radius(), y));

            String instanceId = "f" + idCounter.incrementAndGet();
            FoodEntity food = new FoodEntity(instanceId, chosen, x, y);
            currentFoods.put(instanceId, food);
        }
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
