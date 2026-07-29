package com.mimope.server.game;

import com.mimope.server.protocol.outbound.EvolutionOptionsMessage;

import java.util.Collection;

/** Emits evolution choices once per eligibility window. */
final class EvolutionSystem {
    void update(Collection<PlayerEntity> players, WorldEventBuffer events) {
        for (PlayerEntity player : players) {
            if (!player.isAlive() || !player.shouldSendEvolutionOptions()) continue;
            var options = player.getAvailableEvolutionOptions().stream()
                    .map(a -> new EvolutionOptionsMessage.EvolutionOption(a.id(), a.name(), a.tier()))
                    .toList();
            events.evolutionOptions.add(new GameWorld.EvolutionOptionsEvent(player.getId(), options));
            player.markEvolutionOptionsSent();
        }
    }
}
