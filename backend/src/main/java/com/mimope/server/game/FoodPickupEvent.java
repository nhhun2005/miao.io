package com.mimope.server.game;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable record describing a food pickup that occurred during a single tick.
 * <p>
 * Broadcast to clients so they can display visual feedback (e.g. "+5 XP"
 * floating text or a scale-out animation at the food's last position).
 */
public record FoodPickupEvent(
        /** The instance ID of the consumed food. */
        String foodInstanceId,
        /** The food type ID (e.g. "berry"). */
        String foodId,
        /** X position where the food was collected. */
        double x,
        /** Y position where the food was collected. */
        double y,
        /** XP awarded to the player. */
        int xp,
        /** ID of the player who collected the food. */
        String playerId
) {

    /** Convert to a map for JSON serialization. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("foodInstanceId", foodInstanceId);
        m.put("foodId", foodId);
        m.put("x", x);
        m.put("y", y);
        m.put("xp", xp);
        m.put("playerId", playerId);
        return m;
    }
}
