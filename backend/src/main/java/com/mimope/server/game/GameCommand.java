package com.mimope.server.game;

import java.util.concurrent.CompletableFuture;

/** A mutation that is executed by the authoritative game-loop thread. */
sealed interface GameCommand permits GameCommand.Join, GameCommand.Remove,
        GameCommand.Evolve, GameCommand.ForceKill, GameCommand.GrantXp {

    void execute(GameWorld world);

    record Join(String playerId, String nickname, String starterAnimalId, int maxPlayers,
                CompletableFuture<PlayerEntity> result) implements GameCommand {
        @Override
        public void execute(GameWorld world) {
            if (world.getPlayerCount() >= maxPlayers && world.getPlayer(playerId) == null) {
                result.complete(null);
                return;
            }
            result.complete(world.spawnPlayer(playerId, nickname, starterAnimalId));
        }
    }

    record Remove(String playerId) implements GameCommand {
        @Override
        public void execute(GameWorld world) {
            world.removePlayer(playerId);
        }
    }

    record Evolve(String playerId, String animalId,
                  CompletableFuture<GameWorld.EvolutionResult> result) implements GameCommand {
        @Override
        public void execute(GameWorld world) {
            result.complete(world.evolvePlayer(playerId, animalId));
        }
    }

    record ForceKill(String playerId, CompletableFuture<Boolean> result) implements GameCommand {
        @Override
        public void execute(GameWorld world) {
            result.complete(world.forceKillNow(playerId));
        }
    }

    record GrantXp(String playerId, double amount, CompletableFuture<Double> result) implements GameCommand {
        @Override
        public void execute(GameWorld world) {
            PlayerEntity player = world.getPlayer(playerId);
            if (player == null || !player.isAlive()) {
                result.complete(null);
                return;
            }
            player.addXp(amount);
            result.complete(player.getXp());
        }
    }
}
