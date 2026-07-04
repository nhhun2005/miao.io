package com.mimope.server.protocol.outbound;

import com.mimope.server.protocol.ProtocolConstants;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Periodic world snapshot broadcast to each player.
 *
 * <pre>
 * {
 *   "type": "snapshot",
 *   "tick": 1234,
 *   "players": [ ... ],
 *   "foods": [ ... ],
 *   "leaderboard": [ ... ]
 * }
 * </pre>
 */
public record SnapshotMessage(
        long tick,
        List<PlayerData> players,
        List<FoodData> foods,
        List<LeaderboardEntry> leaderboard,
        List<FoodPickupData> foodPickups,
        List<KillEventData> killEvents,
        List<AbilityEventData> abilityEvents,
        List<GridCellDebug> gridDebug
) {

    public static final String TYPE = ProtocolConstants.TYPE_SNAPSHOT;

    /** Player data included in each snapshot. */
    public record PlayerData(
            String id,
            String nickname,
            double x,
            double y,
            double radius,
            double angle,
            String animalId,
            String skinId,
            double health,
            double maxHealth,
            double xp,
            long abilityCooldownTicks
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("nickname", nickname);
            m.put("x", x);
            m.put("y", y);
            m.put("radius", radius);
            m.put("angle", angle);
            m.put("animalId", animalId);
            m.put("skinId", skinId);
            m.put("health", health);
            m.put("maxHealth", maxHealth);
            m.put("xp", xp);
            m.put("abilityCooldownTicks", abilityCooldownTicks);
            return m;
        }
    }

    /** Food data included in each snapshot. */
    public record FoodData(
            String id,
            String foodId,
            double x,
            double y
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("foodId", foodId);
            m.put("x", x);
            m.put("y", y);
            return m;
        }
    }

    /** Leaderboard entry included in each snapshot. */
    public record LeaderboardEntry(
            String nickname,
            double xp
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nickname", nickname);
            m.put("xp", xp);
            return m;
        }
    }

    /** Food pickup event data included in each snapshot for visual feedback. */
    public record FoodPickupData(
            String foodInstanceId,
            String foodId,
            double x,
            double y,
            int xp,
            String playerId
    ) {
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

    /** Kill event data included in snapshots for visual feedback. */
    public record KillEventData(
            String victimId,
            String killerId,
            String killerNickname,
            double x,
            double y,
            double xpAwarded
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("victimId", victimId);
            m.put("killerId", killerId);
            m.put("killerNickname", killerNickname);
            m.put("x", x);
            m.put("y", y);
            m.put("xpAwarded", xpAwarded);
            return m;
        }
    }

    /** Ability event data included in snapshots for visual effects. */
    public record AbilityEventData(
            String playerId,
            String abilityId,
            double x,
            double y,
            double angle
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("playerId", playerId);
            m.put("abilityId", abilityId);
            m.put("x", x);
            m.put("y", y);
            m.put("angle", angle);
            return m;
        }
    }

    /** Grid cell debug info for spatial-grid visualization on the frontend. */
    public record GridCellDebug(
            double x,
            double y,
            double w,
            double h,
            int playerCount,
            int foodCount
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("x", x);
            m.put("y", y);
            m.put("w", w);
            m.put("h", h);
            m.put("playerCount", playerCount);
            m.put("foodCount", foodCount);
            return m;
        }
    }

    /** Convert to a field map suitable for {@link com.mimope.server.websocket.MessageEncoder}. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tick", tick);
        map.put("players", players.stream().map(PlayerData::toMap).toList());
        map.put("foods", foods.stream().map(FoodData::toMap).toList());
        map.put("leaderboard", leaderboard.stream().map(LeaderboardEntry::toMap).toList());
        if (foodPickups != null && !foodPickups.isEmpty()) {
            map.put("foodPickups", foodPickups.stream().map(FoodPickupData::toMap).toList());
        }
        if (killEvents != null && !killEvents.isEmpty()) {
            map.put("killEvents", killEvents.stream().map(KillEventData::toMap).toList());
        }
        if (abilityEvents != null && !abilityEvents.isEmpty()) {
            map.put("abilityEvents", abilityEvents.stream().map(AbilityEventData::toMap).toList());
        }
        if (gridDebug != null && !gridDebug.isEmpty()) {
            map.put("gridDebug", gridDebug.stream().map(GridCellDebug::toMap).toList());
        }
        return map;
    }
}
