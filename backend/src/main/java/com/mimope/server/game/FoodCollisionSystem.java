package com.mimope.server.game;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Resolves food pickups; a membership set guarantees one award per food per tick. */
final class FoodCollisionSystem {
    void update(Collection<PlayerEntity> players, Map<String, FoodEntity> foods,
                SpatialGrid grid, WorldEventBuffer events) {
        if (foods.isEmpty()) return;
        Set<String> consumedIds = new HashSet<>();
        for (PlayerEntity player : players) {
            if (!player.isAlive()) continue;
            for (FoodEntity food : grid.queryFoods(
                    player.getX(), player.getY(), player.getRadius() + 50)) {
                if (consumedIds.contains(food.getInstanceId())
                        || !food.getDefinition().canBeEatenByTier(player.getAnimal().tier())) continue;
                double dx = player.getX() - food.getX();
                double dy = player.getY() - food.getY();
                double touch = player.getRadius() + food.getRadius();
                if (dx * dx + dy * dy > touch * touch) continue;
                player.addXp(food.getXp());
                player.refillWaterOnFood();
                consumedIds.add(food.getInstanceId());
                events.foodPickups.add(new FoodPickupEvent(
                        food.getInstanceId(), food.getFoodId(), food.getX(), food.getY(),
                        food.getXp(), player.getId()));
            }
        }
        consumedIds.forEach(foods::remove);
    }
}
