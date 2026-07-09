package com.mimope.server.game;

import com.mimope.server.protocol.inbound.InputMessage;
import com.mimope.server.protocol.outbound.EvolutionOptionsMessage;
import com.mimope.server.protocol.outbound.DeathMessage;
import com.mimope.server.protocol.outbound.SnapshotMessage;
import com.mimope.server.websocket.ClientSession;
import com.mimope.server.websocket.MessageEncoder;
import com.mimope.server.websocket.SessionRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates a single game room: owns the {@link GameWorld} and
 * {@link GameLoop}, handles player join/leave, and broadcasts
 * {@link SnapshotMessage}s to all connected clients after each tick.
 * <p>
 * In Phase 0 we decided to start with a single room and 50 players max.
 * Multiple rooms can be added in Phase 21.
 */
@Component
public class GameRoom {

    private static final Logger log = LoggerFactory.getLogger(GameRoom.class);

    private final SessionRegistry sessionRegistry;
    private final MessageEncoder messageEncoder;
    private final SnapshotMetrics snapshotMetrics;

    private final double worldWidth;
    private final double worldHeight;
    private final int maxPlayers;
    private final int maxFood;
    private final int tickRate;

    private GameWorld world;
    private GameLoop loop;
    private volatile boolean gridDebugEnabled = false;

    public GameRoom(SessionRegistry sessionRegistry,
                    MessageEncoder messageEncoder,
                    SnapshotMetrics snapshotMetrics,
                    @Value("${game.world.width:5000}") double worldWidth,
                    @Value("${game.world.height:5000}") double worldHeight,
                    @Value("${game.room.max-players:50}") int maxPlayers,
                    @Value("${game.world.max-food:200}") int maxFood,
                    @Value("${game.loop.tick-rate:20}") int tickRate) {
        this.sessionRegistry = sessionRegistry;
        this.messageEncoder = messageEncoder;
        this.snapshotMetrics = snapshotMetrics;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.maxPlayers = maxPlayers;
        this.maxFood = maxFood;
        this.tickRate = tickRate;
    }

    // ------------------------------------------------------------------ lifecycle

    @PostConstruct
    public void init() {
        world = new GameWorld(worldWidth, worldHeight, maxFood);
        loop = new GameLoop(world, tickRate, this::broadcastSnapshots);
        loop.start();
        log.info("GameRoom initialised: world={}x{}, maxPlayers={}, maxFood={}, tickRate={}",
                worldWidth, worldHeight, maxPlayers, maxFood, tickRate);
    }

    @PreDestroy
    public void shutdown() {
        log.info("GameRoom shutting down...");
        if (loop != null) {
            loop.stop();
        }
        log.info("GameRoom shut down. Final tick metrics: last={}µs, avg={}µs, max={}µs",
                loop != null ? loop.getLastTickDurationMicros() : 0,
                loop != null ? loop.getAverageTickDurationMicros() : 0,
                loop != null ? loop.getMaxTickDurationMicros() : 0);
    }

    // ------------------------------------------------------------------ player management

    /**
     * Add a player to the game world.
     *
     * @param playerId the session ID
     * @param nickname the validated nickname
     * @return the spawned player entity, or {@code null} if the room is full
     */
    public PlayerEntity addPlayer(String playerId, String nickname) {
        return addPlayer(playerId, nickname, null);
    }

    public PlayerEntity addPlayer(String playerId, String nickname, String starterAnimalId) {
        if (world.getPlayerCount() >= maxPlayers) {
            log.warn("Room full ({}/{}), rejecting player '{}'", world.getPlayerCount(), maxPlayers, nickname);
            return null;
        }
        return world.spawnPlayer(playerId, nickname, starterAnimalId);
    }

    /**
     * Remove a player from the game world (disconnect or death cleanup).
     */
    public void removePlayer(String playerId) {
        world.removePlayer(playerId);
    }

    /**
     * Force-kill a player (test support). Emits a death event so the victim's
     * client receives a death message on the next snapshot broadcast.
     */
    public boolean forceKill(String playerId) {
        return world.forceKill(playerId);
    }

    /**
     * Queue a player input for processing on the next tick.
     */
    public void queueInput(String playerId, InputMessage input) {
        world.queueInput(playerId, input);
    }

    /**
     * Check whether a player exists in the world.
     */
    public boolean hasPlayer(String playerId) {
        return world.getPlayer(playerId) != null;
    }

    // ------------------------------------------------------------------ snapshot broadcasting

    /**
     * Build and broadcast a snapshot to every connected, joined session.
     * Called by the game loop after each tick.
     * <p>
     * In Phase 11, snapshots are filtered by viewport radius per player
     * using the spatial grid. Each player only receives entities within
     * their visible range, dramatically reducing snapshot size.
     */
    private void broadcastSnapshots() {
        if (world.getPlayerCount() == 0) {
            return; // No players — skip snapshot
        }

        // Build leaderboard once (same for all players)
        List<SnapshotMessage.LeaderboardEntry> leaderboard = world.getPlayers().stream()
                .filter(PlayerEntity::isAlive)
                .sorted(Comparator.comparingDouble(PlayerEntity::getXp).reversed())
                .limit(10)
                .map(p -> new SnapshotMessage.LeaderboardEntry(p.getNickname(), p.getXp()))
                .toList();

        // Build food pickup events once
        List<SnapshotMessage.FoodPickupData> foodPickups = world.getFoodPickupEvents().stream()
                .map(e -> new SnapshotMessage.FoodPickupData(
                        e.foodInstanceId(),
                        e.foodId(),
                        e.x(),
                        e.y(),
                        e.xp(),
                        e.playerId()
                ))
                .toList();

        List<SnapshotMessage.KillEventData> killEvents = world.getDeathEvents().stream()
                .filter(e -> e.killerId() != null)
                .map(e -> new SnapshotMessage.KillEventData(
                        e.victimId(),
                        e.killerId(),
                        e.killerNickname(),
                        e.x(),
                        e.y(),
                        e.xpAwarded()
                ))
                .toList();

        List<SnapshotMessage.AbilityEventData> abilityEvents = world.getAbilityEvents().stream()
                .map(e -> new SnapshotMessage.AbilityEventData(
                        e.playerId(),
                        e.abilityId(),
                        e.x(),
                        e.y(),
                        e.angle()
                ))
                .toList();

        sendEvolutionOptions();
        sendDeathMessages();

        // Send per-player filtered snapshots
        for (ClientSession session : sessionRegistry.getAll()) {
            if (session.getNickname() == null) continue; // not yet joined
            if (!session.isOpen()) continue;

            String playerId = session.getId();
            SpatialGrid.NearbyQueryResult visible = world.getVisibleEntities(playerId);

            // Build filtered player data list (always include self)
            List<SnapshotMessage.PlayerData> playerDataList = new ArrayList<>();
            Set<String> visiblePlayerIds = new HashSet<>();
            for (PlayerEntity p : visible.players()) {
                visiblePlayerIds.add(p.getId());
                playerDataList.add(toPlayerData(p));
            }
            // Always include self even if outside view radius (so camera works)
            PlayerEntity self = world.getPlayer(playerId);
            if (self != null && self.isAlive() && !visiblePlayerIds.contains(playerId)) {
                playerDataList.add(toPlayerData(self));
            }

            // Build filtered food data list
            List<SnapshotMessage.FoodData> foodDataList = visible.foods().stream()
                    .map(f -> new SnapshotMessage.FoodData(
                            f.getInstanceId(),
                            f.getFoodId(),
                            f.getX(),
                            f.getY()
                    ))
                    .toList();

            List<SnapshotMessage.GridCellDebug> gridDebug = gridDebugEnabled
                    ? world.getGridDebugInfo()
                    : null;

            SnapshotMessage snapshot = new SnapshotMessage(
                    world.getTick(),
                    playerDataList,
                    foodDataList,
                    leaderboard,
                    foodPickups,
                    killEvents,
                    abilityEvents,
                    gridDebug);

            // Measure snapshot size reduction
            int filteredSize = estimateJsonSize(snapshot.toMap());
            int unfilteredSize = estimateUnfilteredSize(leaderboard, foodPickups);
            snapshotMetrics.record(filteredSize, unfilteredSize);

            messageEncoder.send(session, SnapshotMessage.TYPE, snapshot.toMap());
        }
    }

    private void sendEvolutionOptions() {
        for (GameWorld.EvolutionOptionsEvent event : world.getEvolutionOptionsEvents()) {
            ClientSession session = sessionRegistry.get(event.playerId());
            if (session == null || !session.isOpen()) {
                continue;
            }

            EvolutionOptionsMessage message = new EvolutionOptionsMessage(event.options());
            messageEncoder.send(session, EvolutionOptionsMessage.TYPE, message.toMap());
        }
    }

    private void sendDeathMessages() {
        for (DeathEvent event : world.getDeathEvents()) {
            ClientSession session = sessionRegistry.get(event.victimId());
            if (session == null || !session.isOpen()) {
                continue;
            }

            DeathMessage message = new DeathMessage(
                    event.reason(),
                    event.killerNickname(),
                    event.xpAwarded(),
                    event.survivalTimeMs()
            );
            messageEncoder.send(session, DeathMessage.TYPE, message.toMap());
        }
    }

    private SnapshotMessage.PlayerData toPlayerData(PlayerEntity p) {
        return new SnapshotMessage.PlayerData(
                p.getId(),
                p.getNickname(),
                p.getX(),
                p.getY(),
                p.getRadius(),
                p.getAngle(),
                p.getAnimal().id(),
                p.getSkinId(),
                p.getHealth(),
                p.getMaxHealth(),
                p.getXp(),
                p.getOceanSurvival(),
                p.getMaxOceanSurvival(),
                p.getAbilityCooldownRemainingTicks(world.getTick())
        );
    }

    // ------------------------------------------------------------------ snapshot size estimation

    /**
     * Rough JSON byte-size estimate for a map (used for metrics, not exact).
     */
    private int estimateJsonSize(Map<String, Object> map) {
        // Simple heuristic: serialize to string and count bytes
        StringBuilder sb = new StringBuilder();
        appendMap(sb, map);
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    @SuppressWarnings("unchecked")
    private void appendMap(StringBuilder sb, Map<String, Object> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            appendValue(sb, e.getValue());
        }
        sb.append('}');
    }

    @SuppressWarnings("unchecked")
    private void appendValue(StringBuilder sb, Object value) {
        if (value instanceof String) {
            sb.append('"').append(value).append('"');
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof java.util.List) {
            sb.append('[');
            boolean first = true;
            for (Object item : (java.util.List<?>) value) {
                if (!first) sb.append(',');
                first = false;
                appendValue(sb, item);
            }
            sb.append(']');
        } else if (value instanceof Map) {
            appendMap(sb, (Map<String, Object>) value);
        } else {
            sb.append("null");
        }
    }

    /**
     * Estimate the size of an unfiltered snapshot (all players + all food).
     */
    private int estimateUnfilteredSize(List<SnapshotMessage.LeaderboardEntry> leaderboard,
                                       List<SnapshotMessage.FoodPickupData> foodPickups) {
        // Build a snapshot with all entities
        List<SnapshotMessage.PlayerData> allPlayers = world.getPlayers().stream()
                .filter(PlayerEntity::isAlive)
                .map(this::toPlayerData)
                .toList();
        List<SnapshotMessage.FoodData> allFood = world.getFoods().stream()
                .map(f -> new SnapshotMessage.FoodData(
                        f.getInstanceId(), f.getFoodId(), f.getX(), f.getY()))
                .toList();
        SnapshotMessage unfiltered = new SnapshotMessage(
                world.getTick(), allPlayers, allFood, leaderboard, foodPickups, null, null, null);
        return estimateJsonSize(unfiltered.toMap());
    }

    // ------------------------------------------------------------------ getters (for metrics/debug)

    public GameWorld getWorld() {
        return world;
    }

    public GameLoop getLoop() {
        return loop;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public boolean isGridDebugEnabled() {
        return gridDebugEnabled;
    }

    public void setGridDebugEnabled(boolean gridDebugEnabled) {
        this.gridDebugEnabled = gridDebugEnabled;
    }
}
