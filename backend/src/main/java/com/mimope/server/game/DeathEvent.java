package com.mimope.server.game;

/**
 * Server-side event emitted when one player kills another.
 */
public record DeathEvent(
        String victimId,
        String killerId,
        String killerNickname,
        String reason,
        double x,
        double y,
        double xpAwarded,
        long survivalTimeMs
) {
    public static final String REASON_EATEN = "eaten";
    public static final String REASON_OCEAN_SURVIVAL = "Your ocean animal dried out on land.";
    public static final String REASON_DEHYDRATION = "You ran out of water and dehydrated.";

}
