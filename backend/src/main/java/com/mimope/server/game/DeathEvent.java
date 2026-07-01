package com.mimope.server.game;

/**
 * Server-side event emitted when one player kills another.
 */
public record DeathEvent(
        String victimId,
        String killerId,
        String killerNickname,
        double x,
        double y,
        double xpAwarded,
        long survivalTimeMs
) {
}
