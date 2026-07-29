package com.mimope.server.game;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

/** Updates drinking water and emits dehydration deaths. */
final class WaterSystem {
    void update(Collection<PlayerEntity> players, Map<String, Long> spawnTimes,
                Predicate<PlayerEntity> inWater, double deltaTime, WorldEventBuffer events) {
        for (PlayerEntity player : players) {
            if (!player.isAlive()) continue;
            player.updateWater(inWater.test(player), deltaTime);
            if (!player.isAlive()) {
                long spawnedAt = spawnTimes.getOrDefault(player.getId(), System.currentTimeMillis());
                events.deaths.add(new DeathEvent(
                        player.getId(), null, null, DeathEvent.REASON_DEHYDRATION,
                        player.getX(), player.getY(), 0.0,
                        Math.max(0, System.currentTimeMillis() - spawnedAt)));
            }
        }
    }
}
