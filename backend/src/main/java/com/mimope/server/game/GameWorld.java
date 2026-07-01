package com.mimope.server.game;

import com.mimope.server.game.data.AnimalDefinition;
import com.mimope.server.game.data.FoodDefinition;
import com.mimope.server.protocol.inbound.InputMessage;
import com.mimope.server.protocol.outbound.SnapshotMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds the authoritative state of one game world: all players, all food,
 * and the world boundaries.
 * <p>
 * The world is updated each tick by the {@link GameLoop}. Thread-safety
 * for player add/remove is handled via {@link ConcurrentHashMap}; the tick
 * itself runs on a single thread so no additional synchronisation is needed
 * for per-tick mutations.
 */
public class GameWorld {

    private static final Logger log = LoggerFactory.getLogger(GameWorld.class);

    private final double width;
    private final double height;
    private final int maxFood;

    private final ConcurrentHashMap<String, PlayerEntity> players = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FoodEntity> foods = new ConcurrentHashMap<>();
    private final AtomicLong foodIdCounter = new AtomicLong(0);

    private final FoodSpawnService foodSpawnService;

    /**
     * List of food pickup events that occurred during the current tick.
     * Cleared at the start of each tick. Used by the frontend for visual feedback.
     */
    private final List<FoodPickupEvent> foodPickupEvents = new ArrayList<>();

    /** Spatial grid for efficient collision and visibility queries. */
    private final SpatialGrid spatialGrid;

    /** Default visibility radius for snapshot filtering. */
    private static final double DEFAULT_VIEW_RADIUS = 2000.0;

    private long tick = 0;

    public GameWorld(double width, double height, int maxFood) {
        this(width, height, maxFood, 200.0);
    }

    public GameWorld(double width, double height, int maxFood, double cellSize) {
        this.width = width;
        this.height = height;
        this.maxFood = maxFood;
        this.foodSpawnService = new FoodSpawnService(width, height, maxFood, foodIdCounter);
        this.spatialGrid = new SpatialGrid(width, height, cellSize);
    }

    // ------------------------------------------------------------------ tick

    /**
     * Advance the world by one tick.
     *
     * @param deltaTime elapsed time in seconds for this tick
     */
    public void tick(double deltaTime) {
        tick++;

        // Clear events from previous tick
        foodPickupEvents.clear();

        // 1. Process player inputs and apply movement
        for (PlayerEntity player : players.values()) {
            if (!player.isAlive()) continue;

            InputMessage input = player.consumeInput();
            if (input != null) {
                player.applyMovement(input, deltaTime, width, height);
            }
        }

        // 2. Rebuild spatial grid with current entity positions for collision queries.
        rebuildSpatialGrid();

        // 3. Check food collisions using spatial grid — award XP, remove collected food
        checkFoodCollisionsSpatial();

        // 4. Despawn stale food
        foodSpawnService.despawnStaleFood(foods, tick);

        // 5. Replenish food if under the cap
        foodSpawnService.replenishFood(foods, tick);

        // 6. Rebuild again after collision removals/despawns/replenishment so
        // visibility snapshots sent immediately after this tick can include the
        // current world contents. Without this, newly spawned food existed in
        // the authoritative map but was absent from the spatial grid until the
        // next tick, producing empty/partial snapshots for freshly joined players.
        rebuildSpatialGrid();

        // Note: player-vs-player predation is Phase 13.
    }

    // ------------------------------------------------------------------ spatial grid

    /**
     * Rebuild the spatial grid by inserting all alive players and food items.
     * Called at the start of each tick after movement is applied.
     */
    private void rebuildSpatialGrid() {
        spatialGrid.clear();
        for (PlayerEntity player : players.values()) {
            if (player.isAlive()) {
                spatialGrid.insert(player);
            }
        }
        for (FoodEntity food : foods.values()) {
            spatialGrid.insert(food);
        }
    }

    /**
     * Get the spatial grid (for testing and metrics).
     */
    public SpatialGrid getSpatialGrid() {
        return spatialGrid;
    }

    /**
     * Query visible entities for a given player based on viewport radius.
     *
     * @param playerId      the player whose visibility to query
     * @param viewRadius    the visibility radius
     * @return nearby players and food within the viewport
     */
    public SpatialGrid.NearbyQueryResult getVisibleEntities(String playerId, double viewRadius) {
        PlayerEntity player = players.get(playerId);
        if (player == null || !player.isAlive()) {
            return new SpatialGrid.NearbyQueryResult(List.of(), List.of());
        }
        return spatialGrid.queryNearby(player.getX(), player.getY(), viewRadius);
    }

    /**
     * Query visible entities for a given player using the default view radius.
     */
    public SpatialGrid.NearbyQueryResult getVisibleEntities(String playerId) {
        return getVisibleEntities(playerId, DEFAULT_VIEW_RADIUS);
    }

    /**
     * Returns debug info for all grid cells (for frontend visualization).
     */
    public List<SnapshotMessage.GridCellDebug> getGridDebugInfo() {
        return spatialGrid.getAllCellsDebug();
    }

    // ------------------------------------------------------------------ food collision

    /**
     * Check every alive player against nearby food items for overlap using the spatial grid.
     * If a player overlaps a food item and meets the tier requirement,
     * the food is consumed: XP is awarded and the food is removed.
     * <p>
     * Uses the spatial grid for O(1) nearby queries instead of O(n) brute force.
     */
    private void checkFoodCollisionsSpatial() {
        if (foods.isEmpty()) return;

        // Collect food IDs to remove (avoid ConcurrentModificationException)
        List<String> consumedIds = new ArrayList<>();

        for (PlayerEntity player : players.values()) {
            if (!player.isAlive()) continue;

            double px = player.getX();
            double py = player.getY();
            double pr = player.getRadius();
            int playerTier = player.getAnimal().tier();

            // Query nearby food using spatial grid instead of iterating all food
            List<FoodEntity> nearbyFood = spatialGrid.queryFoods(px, py, pr + 50);

            for (FoodEntity food : nearbyFood) {
                // Skip if already consumed this tick
                if (consumedIds.contains(food.getInstanceId())) continue;

                // Check tier requirement
                if (!food.getDefinition().canBeEatenByTier(playerTier)) continue;

                // Circle-circle collision
                double dx = px - food.getX();
                double dy = py - food.getY();
                double distSq = dx * dx + dy * dy;
                double touchDist = pr + food.getRadius();

                if (distSq <= touchDist * touchDist) {
                    // Collision! Award XP and mark for removal
                    player.addXp(food.getXp());
                    consumedIds.add(food.getInstanceId());

                    // Record pickup event for visual feedback
                    foodPickupEvents.add(new FoodPickupEvent(
                            food.getInstanceId(),
                            food.getFoodId(),
                            food.getX(),
                            food.getY(),
                            food.getXp(),
                            player.getId()
                    ));

                    log.debug("Player {} ate {} (+{}xp, total={}xp)",
                            player.getNickname(), food.getFoodId(), food.getXp(), player.getXp());
                }
            }
        }

        // Remove consumed food
        for (String id : consumedIds) {
            foods.remove(id);
            foodSpawnService.onFoodConsumed(id);
        }
    }

    // ------------------------------------------------------------------ players

    /**
     * Spawn a new player at a random position within the world.
     *
     * @param playerId the session / player ID
     * @param nickname the validated nickname
     * @return the created player entity
     */
    public PlayerEntity spawnPlayer(String playerId, String nickname) {
        AnimalDefinition starter = AnimalDefinition.starter();
        double x = randomRange(starter.radius(), width - starter.radius());
        double y = randomRange(starter.radius(), height - starter.radius());

        PlayerEntity player = new PlayerEntity(playerId, nickname, starter, x, y);
        players.put(playerId, player);

        log.info("Player spawned: {} at ({}, {})", player, x, y);
        return player;
    }

    /**
     * Remove a player from the world (disconnect or death).
     *
     * @return the removed player, or {@code null}
     */
    public PlayerEntity removePlayer(String playerId) {
        PlayerEntity removed = players.remove(playerId);
        if (removed != null) {
            log.info("Player removed: {}", removed);
        }
        return removed;
    }

    /**
     * Queue an input for a player. Called from the WebSocket handler thread.
     */
    public void queueInput(String playerId, InputMessage input) {
        PlayerEntity player = players.get(playerId);
        if (player != null && player.isAlive()) {
            player.queueInput(input);
        }
    }

    public PlayerEntity getPlayer(String playerId) {
        return players.get(playerId);
    }

    public Collection<PlayerEntity> getPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    public int getPlayerCount() {
        return players.size();
    }

    // ------------------------------------------------------------------ food

    /**
     * Remove a food entity by instance ID (e.g. after pickup).
     */
    public FoodEntity removeFood(String instanceId) {
        FoodEntity removed = foods.remove(instanceId);
        if (removed != null) {
            foodSpawnService.onFoodConsumed(instanceId);
        }
        return removed;
    }

    public Collection<FoodEntity> getFoods() {
        return Collections.unmodifiableCollection(foods.values());
    }

    public int getFoodCount() {
        return foods.size();
    }

    /**
     * Get the list of food pickup events from the current tick.
     * Used to broadcast visual feedback to clients.
     */
    public List<FoodPickupEvent> getFoodPickupEvents() {
        return Collections.unmodifiableList(foodPickupEvents);
    }

    // ------------------------------------------------------------------ world info

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public long getTick() {
        return tick;
    }

    public FoodSpawnService getFoodSpawnService() {
        return foodSpawnService;
    }

    // ------------------------------------------------------------------ helpers

    private static double randomRange(double min, double max) {
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }
}
