package com.mimope.server.game;

import com.mimope.server.game.data.AnimalDefinition;
import com.mimope.server.game.data.AnimalVariantDefinition;
import com.mimope.server.game.data.Biome;
import com.mimope.server.game.data.FoodDefinition;
import com.mimope.server.protocol.inbound.InputMessage;
import com.mimope.server.protocol.outbound.EvolutionOptionsMessage;
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
    private final ConcurrentHashMap<String, Long> playerSpawnMs = new ConcurrentHashMap<>();
    private final AtomicLong foodIdCounter = new AtomicLong(0);

    private final FoodSpawnService foodSpawnService;

    /**
     * List of food pickup events that occurred during the current tick.
     * Cleared at the start of each tick. Used by the frontend for visual feedback.
     */
    private final List<FoodPickupEvent> foodPickupEvents = new ArrayList<>();

    /** Evolution option events emitted during the current tick. */
    private final List<EvolutionOptionsEvent> evolutionOptionsEvents = new ArrayList<>();

    /** Death events emitted during the current tick. */
    private final List<DeathEvent> deathEvents = new ArrayList<>();

    /** Ability events emitted during the current tick. */
    private final List<AbilityEvent> abilityEvents = new ArrayList<>();

    /**
     * Player IDs queued for a forced kill from outside the tick thread
     * (test support only). Drained at the start of each tick so the resulting
     * death event is broadcast in the same tick, avoiding a race with the
     * per-tick event clear.
     */
    private final java.util.Queue<String> pendingForceKills = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** Spatial grid for efficient collision and visibility queries. */
    private final SpatialGrid spatialGrid;

    /** Default visibility radius for snapshot filtering. */
    private static final double DEFAULT_VIEW_RADIUS = 2000.0;
    private static final double DASH_SPEED_MULTIPLIER = 3.0;

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
        evolutionOptionsEvents.clear();
        deathEvents.clear();
        abilityEvents.clear();

        // Drain any externally-requested forced kills (test support) so their
        // death events are emitted within this tick's event window.
        drainForceKills();

        // 1. Process player inputs and apply movement
        for (PlayerEntity player : players.values()) {
            if (!player.isAlive()) continue;

            InputMessage input = player.consumeInput();
            if (input != null) {
                double abilityMultiplier = 1.0;
                if (input.ability() && player.canUseAbility(tick)) {
                    player.markAbilityUsed(tick);
                    abilityMultiplier = applyAbility(player, input);
                    abilityEvents.add(new AbilityEvent(
                            player.getId(), player.getAnimal().abilityId(), player.getX(), player.getY(), input.angle()));
                }
                double biomeMultiplier = movementMultiplierAt(player.getX(), player.getY());
                player.applyMovement(input, deltaTime, width, height, abilityMultiplier * biomeMultiplier);
            }
        }

        // 2. Rebuild spatial grid with current entity positions for collision queries.
        rebuildSpatialGrid();

        // 3. Check food collisions using spatial grid — award XP, remove collected food
        checkFoodCollisionsSpatial();

        // 4. Resolve player-vs-player predation.
        checkPlayerPredation();

        // 5. Send evolution options once a player has enough XP for the next tier.
        checkEvolutionOptions();

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

    private void checkEvolutionOptions() {
        for (PlayerEntity player : players.values()) {
            if (!player.isAlive() || !player.shouldSendEvolutionOptions()) {
                continue;
            }

            List<EvolutionOptionsMessage.EvolutionOption> options = player.getAvailableEvolutionOptions().stream()
                    .map(a -> new EvolutionOptionsMessage.EvolutionOption(a.id(), a.name(), a.tier()))
                    .toList();

            evolutionOptionsEvents.add(new EvolutionOptionsEvent(player.getId(), options));
            player.markEvolutionOptionsSent();
        }
    }

    private void checkPlayerPredation() {
        Set<String> killedThisTick = new HashSet<>();

        for (PlayerEntity predator : players.values()) {
            if (!predator.isAlive()) {
                continue;
            }

            List<PlayerEntity> nearbyPlayers = spatialGrid.queryPlayers(
                    predator.getX(), predator.getY(), predator.getRadius() * 2.5);

            for (PlayerEntity prey : nearbyPlayers) {
                if (predator == prey || !prey.isAlive() || killedThisTick.contains(prey.getId())) {
                    continue;
                }
                if (!predator.getAnimal().canEat(prey.getAnimal())) {
                    continue;
                }

                double dx = predator.getX() - prey.getX();
                double dy = predator.getY() - prey.getY();
                double touchDist = predator.getRadius() + prey.getRadius();
                if (dx * dx + dy * dy > touchDist * touchDist) {
                    continue;
                }

                double xpAwarded = Math.max(50.0, prey.getXp() * 0.25 + prey.getAnimal().tier() * 25.0);
                prey.kill();
                predator.addXp(xpAwarded);
                killedThisTick.add(prey.getId());

                long spawnedAt = playerSpawnMs.getOrDefault(prey.getId(), System.currentTimeMillis());
                deathEvents.add(new DeathEvent(
                        prey.getId(),
                        predator.getId(),
                        predator.getNickname(),
                        prey.getX(),
                        prey.getY(),
                        xpAwarded,
                        Math.max(0, System.currentTimeMillis() - spawnedAt)
                ));

                log.debug("{} ate {} (+{}xp)", predator.getNickname(), prey.getNickname(), xpAwarded);
            }
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
        return spawnPlayer(playerId, nickname, null);
    }

    public PlayerEntity spawnPlayer(String playerId, String nickname, String starterAnimalId) {
        AnimalDefinition starter = AnimalDefinition.isValidStarter(starterAnimalId)
                ? AnimalDefinition.byId(starterAnimalId == null || starterAnimalId.isBlank() ? "mouse" : starterAnimalId)
                : AnimalDefinition.starter();
        double x = randomRange(starter.radius(), width - starter.radius());
        double y = randomRange(starter.radius(), height - starter.radius());

        PlayerEntity player = new PlayerEntity(playerId, nickname, starter, x, y);
        player.setAnimal(starter, rollSkinId(starter));
        players.put(playerId, player);
        playerSpawnMs.put(playerId, System.currentTimeMillis());

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
        playerSpawnMs.remove(playerId);
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

    /**
     * Get the evolution option events from the current tick.
     */
    public List<EvolutionOptionsEvent> getEvolutionOptionsEvents() {
        return Collections.unmodifiableList(evolutionOptionsEvents);
    }

    public List<DeathEvent> getDeathEvents() {
        return Collections.unmodifiableList(deathEvents);
    }

    public List<AbilityEvent> getAbilityEvents() {
        return Collections.unmodifiableList(abilityEvents);
    }

    /**
     * Force a player to die and emit a death event so the victim's client
     * receives a death message on the next broadcast. Intended for test
     * support only; normal deaths flow through {@link #checkPlayerPredation()}.
     *
     * <p>The kill is queued and applied at the start of the next tick (inside
     * {@link #tick(double)}), so the resulting death event is emitted within
     * that tick's event window rather than racing the per-tick event clear.
     *
     * @return {@code true} if the player currently exists and is alive
     */
    public boolean forceKill(String playerId) {
        PlayerEntity victim = players.get(playerId);
        if (victim == null || !victim.isAlive()) {
            return false;
        }
        pendingForceKills.add(playerId);
        return true;
    }

    /** Apply any queued forced kills, emitting death events for each. */
    private void drainForceKills() {
        String playerId;
        while ((playerId = pendingForceKills.poll()) != null) {
            PlayerEntity victim = players.get(playerId);
            if (victim == null || !victim.isAlive()) {
                continue;
            }
            victim.kill();
            long spawnedAt = playerSpawnMs.getOrDefault(playerId, System.currentTimeMillis());
            deathEvents.add(new DeathEvent(
                    playerId,
                    null,
                    "Test",
                    victim.getX(),
                    victim.getY(),
                    0.0,
                    Math.max(0, System.currentTimeMillis() - spawnedAt)
            ));
        }
    }

    public EvolutionResult evolvePlayer(String playerId, String animalId) {
        PlayerEntity player = players.get(playerId);
        if (player == null || !player.isAlive()) {
            return EvolutionResult.failure("Player is not alive.");
        }

        AnimalDefinition target = AnimalDefinition.byId(animalId);
        if (target == null) {
            return EvolutionResult.failure("Unknown animal: " + animalId);
        }

        if (!player.canEvolveTo(target)) {
            return EvolutionResult.failure("Evolution is not available yet.");
        }

        player.setAnimal(target, rollSkinId(target));
        return EvolutionResult.success(player);
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

    public Biome biomeAt(double x, double y) {
        if (x < width * 0.28) {
            return Biome.OCEAN;
        }
        if (y > height * 0.64) {
            return Biome.ARCTIC;
        }
        return Biome.LAND;
    }

    public double movementMultiplierAt(double x, double y) {
        return switch (biomeAt(x, y)) {
            case LAND -> 1.0;
            case OCEAN -> 0.78;
            case ARCTIC -> 0.86;
            case FINAL -> 1.0;
        };
    }

    private static double randomRange(double min, double max) {
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    private double applyAbility(PlayerEntity player, InputMessage input) {
        String abilityId = player.getAnimal().abilityId();
        return switch (abilityId) {
            case "charge" -> 3.2;
            case "ice_slide" -> biomeAt(player.getX(), player.getY()) == Biome.ARCTIC ? 3.4 : 2.4;
            case "burrow_dash", "dig_dash", "stink_dash", "forage_dash", "ink_dash",
                    "snowball_dash", "fire_dash" -> DASH_SPEED_MULTIPLIER;
            case "shell_guard", "inflate_guard" -> {
                player.guardForTicks(tick, 40);
                yield 0.55;
            }
            case "claw", "croc_bite", "back_kick" -> {
                damagePlayersInArc(player, input.angle(), 150, Math.PI / 2, 90);
                yield 1.0;
            }
            case "shock_pulse", "sting_pulse", "roar_pulse", "wave_pulse",
                    "whirlpool_pulse", "freeze_pulse" -> {
                damagePlayersInRadius(player, pulseRadius(abilityId), pulseDamage(abilityId));
                yield 1.0;
            }
            default -> DASH_SPEED_MULTIPLIER;
        };
    }

    private void damagePlayersInRadius(PlayerEntity source, double radius, double damage) {
        for (PlayerEntity target : spatialGrid.queryPlayers(source.getX(), source.getY(), radius)) {
            if (target == source || !target.isAlive() || !source.getAnimal().canEat(target.getAnimal())) {
                continue;
            }
            double dx = source.getX() - target.getX();
            double dy = source.getY() - target.getY();
            double hitDistance = radius + target.getRadius();
            if (dx * dx + dy * dy <= hitDistance * hitDistance) {
                target.damage(damage, tick);
            }
        }
    }

    private void damagePlayersInArc(PlayerEntity source,
                                    double angle,
                                    double range,
                                    double arcRadians,
                                    double damage) {
        for (PlayerEntity target : spatialGrid.queryPlayers(source.getX(), source.getY(), range)) {
            if (target == source || !target.isAlive() || !source.getAnimal().canEat(target.getAnimal())) {
                continue;
            }
            double dx = target.getX() - source.getX();
            double dy = target.getY() - source.getY();
            double distSq = dx * dx + dy * dy;
            double hitDistance = range + target.getRadius();
            if (distSq > hitDistance * hitDistance) {
                continue;
            }
            double targetAngle = Math.atan2(dy, dx);
            double diff = Math.abs(normalizeAngle(targetAngle - angle));
            if (diff <= arcRadians / 2) {
                target.damage(damage, tick);
            }
        }
    }

    private static double pulseRadius(String abilityId) {
        return switch (abilityId) {
            case "roar_pulse", "whirlpool_pulse", "freeze_pulse" -> 220;
            case "wave_pulse" -> 200;
            default -> 160;
        };
    }

    private static double pulseDamage(String abilityId) {
        return switch (abilityId) {
            case "roar_pulse", "whirlpool_pulse", "freeze_pulse" -> 110;
            case "wave_pulse" -> 100;
            default -> 70;
        };
    }

    private static double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= Math.PI * 2;
        while (angle < -Math.PI) angle += Math.PI * 2;
        return angle;
    }

    private String rollSkinId(AnimalDefinition animal) {
        for (AnimalVariantDefinition variant : AnimalVariantDefinition.all().values()) {
            if (variant.baseAnimalId().equals(animal.id())
                    && ThreadLocalRandom.current().nextDouble() < variant.rollRate()) {
                return variant.id();
            }
        }
        return animal.id();
    }

    public record EvolutionOptionsEvent(
            String playerId,
            List<EvolutionOptionsMessage.EvolutionOption> options
    ) {
    }

    public record EvolutionResult(
            boolean success,
            String error,
            PlayerEntity player
    ) {
        public static EvolutionResult success(PlayerEntity player) {
            return new EvolutionResult(true, null, player);
        }

        public static EvolutionResult failure(String error) {
            return new EvolutionResult(false, error, null);
        }
    }
}
