package com.mimope.server.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Owns transient events whose lifetime is exactly one simulation tick. */
final class WorldEventBuffer {
    final List<FoodPickupEvent> foodPickups = new ArrayList<>();
    final List<GameWorld.EvolutionOptionsEvent> evolutionOptions = new ArrayList<>();
    final List<DeathEvent> deaths = new ArrayList<>();
    final List<DashEvent> dashes = new ArrayList<>();

    void clear() {
        foodPickups.clear();
        evolutionOptions.clear();
        deaths.clear();
        dashes.clear();
    }

    List<FoodPickupEvent> foodPickups() { return Collections.unmodifiableList(foodPickups); }
    List<GameWorld.EvolutionOptionsEvent> evolutionOptions() { return Collections.unmodifiableList(evolutionOptions); }
    List<DeathEvent> deaths() { return Collections.unmodifiableList(deaths); }
    List<DashEvent> dashes() { return Collections.unmodifiableList(dashes); }
}
