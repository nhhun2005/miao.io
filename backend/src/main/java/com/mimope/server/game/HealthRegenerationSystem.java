package com.mimope.server.game;

import java.util.Collection;

/** Applies passive regeneration to living players. */
final class HealthRegenerationSystem {
    void update(Collection<PlayerEntity> players, double deltaTime) {
        players.stream().filter(PlayerEntity::isAlive).forEach(p -> p.regenerateHealth(deltaTime));
    }
}
