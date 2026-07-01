package com.mimope.server.game;

/**
 * Server-side event emitted when an ability is successfully activated.
 */
public record AbilityEvent(
        String playerId,
        String abilityId,
        double x,
        double y,
        double angle
) {
}
