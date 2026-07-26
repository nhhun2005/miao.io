package com.mimope.server.game;

/**
 * Server-side event emitted when a player successfully dashes. Used by the
 * frontend to spawn a dash visual effect.
 */
public record DashEvent(
        String playerId,
        double x,
        double y,
        double angle
) {
}
