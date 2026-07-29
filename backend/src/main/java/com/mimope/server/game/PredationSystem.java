package com.mimope.server.game;

/**
 * Tick-stage boundary for player predation.
 *
 * The resolver remains world-owned because it needs the world's bite cooldown
 * index and spawn timestamps; this boundary keeps orchestration explicit
 * without duplicating that authoritative state.
 */
final class PredationSystem {
    void update(double deltaTime, Resolver resolver) {
        resolver.resolve(deltaTime);
    }

    @FunctionalInterface
    interface Resolver {
        void resolve(double deltaTime);
    }
}
