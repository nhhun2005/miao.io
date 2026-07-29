package com.mimope.server.game;

import com.mimope.server.game.data.AnimalDefinition;
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
    private final Map<String, Long> lastBiteTickByPair = new HashMap<>();
    private final AtomicLong foodIdCounter = new AtomicLong(0);

    private final FoodSpawnService foodSpawnService;

    /**
     * List of food pickup events that occurred during the current tick.
     * Cleared at the start of each tick. Used by the frontend for visual feedback.
     */
    private final WorldEventBuffer events = new WorldEventBuffer();
    private final MovementSystem movementSystem = new MovementSystem();
    private final WaterSystem waterSystem = new WaterSystem();
    private final HealthRegenerationSystem healthRegenerationSystem = new HealthRegenerationSystem();
    private final FoodCollisionSystem foodCollisionSystem = new FoodCollisionSystem();
    private final EvolutionSystem evolutionSystem = new EvolutionSystem();
    private final PredationSystem predationSystem = new PredationSystem();

    /**
     * Player IDs queued for a forced kill from outside the tick thread
     * (test support only). Drained at the start of each tick so the resulting
     * death event is broadcast in the same tick, avoiding a race with the
     * per-tick event clear.
     */
    private final GameCommandQueue commandQueue = new GameCommandQueue();

    /** Spatial grid for efficient collision and visibility queries. */
    private final SpatialGrid spatialGrid;

    /** Default visibility radius for snapshot filtering. */
    private static final double DEFAULT_VIEW_RADIUS = 2000.0;
    /** Speed multiplier applied while a dash burst is active. */
    private static final double DASH_SPEED_MULTIPLIER = 3.0;
    private static final long BITE_COOLDOWN_TICKS = 20;
    private static final double BITE_ARC_RADIANS = Math.PI * 2.0 / 3.0;
    /**
     * Total angular width of the vulnerable tail zone on a higher-tier animal.
     * Lower-tier creatures may counter-attack only from this narrow 30-degree
     * cone, preventing bites from either flank from counting as rear bites.
     */
    private static final double REAR_BITE_ARC_RADIANS = Math.PI / 6.0;

    /**
     * Knockback distance applied to a bitten creature, expressed as a fraction
     * of a full dash's travel distance. A bite shoves the victim away from the
     * attacker by 0.75x the distance a dash would cover.
     */
    private static final double BITE_KNOCKBACK_DASH_FRACTION = 0.75;

    /**
     * Height in world pixels of the river band that crosses the land biome.
     * Mirrors the river rectangle drawn client-side in
     * PixiGame.renderBackground so the visual and the water source align.
     */
    private static final double RIVER_HEIGHT = 150.0;


    private long tick = 0;

    /**
     * Water puddles scattered across the land and arctic biomes. Aquatic
     * animals refill their drinking-water bar while standing in one, just as
     * they do in the ocean. Positions are expressed as fractions of the world
     * size so they stay consistent with the frontend rendering.
     */
    private final List<Puddle> puddles;

    public GameWorld(double width, double height, int maxFood) {
        this(width, height, maxFood, 200.0);
    }

    public GameWorld(double width, double height, int maxFood, double cellSize) {
        this.width = width;
        this.height = height;
        this.maxFood = maxFood;
        this.foodSpawnService = new FoodSpawnService(width, height, maxFood, foodIdCounter);
        this.spatialGrid = new SpatialGrid(width, height, cellSize);
        this.puddles = buildPuddles(width, height);
    }

    private static List<Puddle> buildPuddles(double width, double height) {
        return List.of(
                // Grassland ponds
                new Puddle(width * 0.45, height * 0.22, 190),
                new Puddle(width * 0.72, height * 0.14, 150),
                // Kept south of the river with the full radius clear of it.
                new Puddle(width * 0.86, height * 0.50, 210),
                new Puddle(width * 0.55, height * 0.38, 170),
                new Puddle(width * 0.38, height * 0.52, 160),
                // Arctic ponds (southern band)
                new Puddle(width * 0.48, height * 0.80, 180),
                new Puddle(width * 0.80, height * 0.88, 200),
                new Puddle(width * 0.64, height * 0.72, 170)
        );
    }



    // ------------------------------------------------------------------ tick

    /**
     * Advance the world by one tick.
     *
     * @param deltaTime elapsed time in seconds for this tick
     */
    public void tick(double deltaTime) {
        tick++;

        // Clear the prior tick before commands: force-kill commands emit death
        // events that must survive through this tick's broadcast.
        events.clear();

        // All externally requested world mutations happen here, on the
        // authoritative loop thread, before simulation observes world state.
        commandQueue.drain(this);

        movementSystem.update(players.values(), deltaTime, width, height, tick,
                p -> movementMultiplierFor(p.getAnimal(), biomeAt(p.getX(), p.getY())), events);

        // 2. Update every creature's drinking-water bar (refill in water,
        // dehydrate when empty on land).
        waterSystem.update(players.values(), playerSpawnMs,
                p -> isInWaterSource(p.getX(), p.getY()), deltaTime, events);

        // 2b. Passively regenerate health for creatures that have avoided
        // damage long enough.
        healthRegenerationSystem.update(players.values(), deltaTime);

        // 3. Rebuild spatial grid with current entity positions for collision queries.
        rebuildSpatialGrid();

        // 3. Check food collisions using spatial grid — award XP, remove collected food
        foodCollisionSystem.update(players.values(), foods, spatialGrid, events);

        // 4. Resolve player-vs-player predation.
        predationSystem.update(deltaTime, this::checkPlayerPredation);

        // 5. Send evolution options once a player has enough XP for the next tier.
        evolutionSystem.update(players.values(), events);

        // 4. Replenish consumed food. Uneaten food is never removed merely
        // because of its age, so it cannot vanish while a player approaches.
        foodSpawnService.replenishFood(foods);

        // 5. Rebuild again after collision removals/replenishment so
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

    private void checkPlayerPredation(double deltaTime) {
        Set<String> killedThisTick = new HashSet<>();
        List<PendingKnockback> knockbacks = new ArrayList<>();

        for (PlayerEntity attacker : players.values()) {
            if (!attacker.isAlive()) {
                continue;
            }

            List<PlayerEntity> nearbyPlayers = spatialGrid.queryPlayers(
                    attacker.getX(), attacker.getY(), attacker.getRadius() * 2.5);

            for (PlayerEntity target : nearbyPlayers) {
                if (killedThisTick.contains(target.getId())) {
                    continue;
                }
                BiteResult result = applyBite(attacker, target, deltaTime, knockbacks);
                if (result.killed()) {
                    killedThisTick.add(target.getId());
                }
            }
        }

        // Apply knockbacks only after every bite this tick has been resolved.
        // Pushing a victim the instant it is bitten would otherwise shove it out
        // of range before a mutual/simultaneous attacker gets its own bite in.
        for (PendingKnockback kb : knockbacks) {
            if (kb.target().isAlive()) {
                kb.target().applyKnockback(kb.sourceX(), kb.sourceY(), kb.distance(), width, height);
            }
        }
    }

    /** A knockback queued during predation, applied once all bites resolve. */
    private record PendingKnockback(PlayerEntity target, double sourceX, double sourceY, double distance) {
    }

    private BiteResult applyBite(PlayerEntity attacker, PlayerEntity target, double deltaTime,
                                 List<PendingKnockback> knockbacks) {
        if (attacker == null || target == null || attacker == target) {
            return BiteResult.noHit();
        }
        if (!attacker.isAlive() || !target.isAlive()) {
            return BiteResult.noHit();
        }
        if (attacker.getAnimal().tier() == target.getAnimal().tier()) {
            return BiteResult.noHit();
        }
        if (!isBiteCollision(attacker, target) || !isFacingTarget(attacker, target)) {
            return BiteResult.noHit();
        }
        if (attacker.getAnimal().tier() < target.getAnimal().tier()
                && !isInTargetRearBiteZone(attacker, target)) {
            return BiteResult.noHit();
        }
        if (!canBiteNow(attacker.getId(), target.getId())) {
            return BiteResult.noHit();
        }

        boolean lethal = target.getHealth() <= 1;
        double stolenXp = transferXpOnBite(attacker, target, lethal);
        target.damageByBite();
        knockbacks.add(buildBiteKnockback(attacker, target, deltaTime));
        log.debug("{} bit {} (-1hp, stolenXp={}, health={}/{})",
                attacker.getNickname(), target.getNickname(), stolenXp, target.getHealth(), target.getMaxHealth());

        if (!target.isDeadByHealth()) {
            return new BiteResult(false, stolenXp);
        }

        target.kill();
        clearBiteCooldownsForPlayer(target.getId());
        long spawnedAt = playerSpawnMs.getOrDefault(target.getId(), System.currentTimeMillis());
        events.deaths.add(new DeathEvent(
                target.getId(),
                attacker.getId(),
                attacker.getNickname(),
                DeathEvent.REASON_EATEN,
                target.getX(),
                target.getY(),
                stolenXp,
                Math.max(0, System.currentTimeMillis() - spawnedAt)
        ));

        log.debug("{} bit {} to death (+{}xp stolen)", attacker.getNickname(), target.getNickname(), stolenXp);
        return new BiteResult(true, stolenXp);
    }

    /**
     * Shove a freshly bitten creature directly away from its attacker. The push
     * distance is {@link #BITE_KNOCKBACK_DASH_FRACTION} of the attacker's full
     * dash range, so being bitten always throws the victim a fixed fraction of
     * a dash away regardless of who delivered the bite (a higher-tier predator
     * or a same-facing "rear" bite included).
     */
    private PendingKnockback buildBiteKnockback(PlayerEntity attacker, PlayerEntity target, double deltaTime) {
        double distance = attacker.dashDistance(deltaTime, DASH_SPEED_MULTIPLIER)
                * BITE_KNOCKBACK_DASH_FRACTION;
        return new PendingKnockback(target, attacker.getX(), attacker.getY(), distance);
    }

    private double transferXpOnBite(PlayerEntity attacker, PlayerEntity target, boolean lethal) {
        double stolenXp = lethal
                ? Math.max(0, target.getXp())
                : calculateTenPercentXpSteal(target);

        if (stolenXp > 0) {
            target.setXp(target.getXp() - stolenXp);
            attacker.addXp(stolenXp);
        }

        return stolenXp;
    }

    private double calculateTenPercentXpSteal(PlayerEntity target) {
        double targetXp = Math.max(0, target.getXp());
        if (targetXp <= 0) {
            return 0;
        }
        double stolenXp = Math.max(1, Math.floor(targetXp * 0.10));
        return Math.min(stolenXp, targetXp);
    }

    private boolean isBiteCollision(PlayerEntity attacker, PlayerEntity target) {
        double dx = attacker.getX() - target.getX();
        double dy = attacker.getY() - target.getY();
        double touchDist = attacker.getRadius() + target.getRadius();
        return dx * dx + dy * dy <= touchDist * touchDist;
    }

    private boolean isFacingTarget(PlayerEntity attacker, PlayerEntity target) {
        double dx = target.getX() - attacker.getX();
        double dy = target.getY() - attacker.getY();
        if (dx == 0 && dy == 0) {
            return true;
        }
        double targetAngle = Math.atan2(dy, dx);
        double diff = Math.abs(normalizeAngle(targetAngle - attacker.getAngle()));
        return diff <= BITE_ARC_RADIANS / 2.0;
    }

    /**
     * Check the special counter-attack zone behind a higher-tier target. The
     * angle from the target to the attacker must be almost exactly opposite
     * the target's facing direction; ordinary side contact is not sufficient.
     */
    private boolean isInTargetRearBiteZone(PlayerEntity attacker, PlayerEntity target) {
        double dx = attacker.getX() - target.getX();
        double dy = attacker.getY() - target.getY();
        if (dx == 0 && dy == 0) {
            return false;
        }
        double attackerAngleFromTarget = Math.atan2(dy, dx);
        double targetRearAngle = target.getAngle() + Math.PI;
        double diff = Math.abs(normalizeAngle(attackerAngleFromTarget - targetRearAngle));
        return diff <= REAR_BITE_ARC_RADIANS / 2.0;
    }

    private boolean canBiteNow(String attackerId, String targetId) {
        String key = biteKey(attackerId, targetId);
        Long lastTick = lastBiteTickByPair.get(key);
        if (lastTick == null || tick - lastTick >= BITE_COOLDOWN_TICKS) {
            lastBiteTickByPair.put(key, tick);
            return true;
        }
        return false;
    }

    private static String biteKey(String attackerId, String targetId) {
        return attackerId + "->" + targetId;
    }

    private void clearBiteCooldownsForPlayer(String playerId) {
        String outgoingPrefix = playerId + "->";
        String incomingSuffix = "->" + playerId;
        lastBiteTickByPair.keySet().removeIf(key -> key.startsWith(outgoingPrefix) || key.endsWith(incomingSuffix));
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
        SpawnPoint spawnPoint = randomSpawnPointForBiome(starter.biome(), starter.radius());

        PlayerEntity player = new PlayerEntity(playerId, nickname, starter, spawnPoint.x(), spawnPoint.y());
        player.setAnimal(starter);
        players.put(playerId, player);
        playerSpawnMs.put(playerId, System.currentTimeMillis());

        log.info("Player spawned: {} at ({}, {})", player, spawnPoint.x(), spawnPoint.y());
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
        clearBiteCooldownsForPlayer(playerId);
        if (removed != null) {
            log.info("Player removed: {}", removed);
        }
        return removed;
    }

    /**
     * Queue an input for a player. Called from the WebSocket handler thread.
     */
    public boolean queueInput(String playerId, InputMessage input) {
        PlayerEntity player = players.get(playerId);
        if (player != null && player.isAlive()) {
            return player.queueInput(input);
        }
        return false;
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
        return foods.remove(instanceId);
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
        return events.foodPickups();
    }

    /**
     * Get the evolution option events from the current tick.
     */
    public List<EvolutionOptionsEvent> getEvolutionOptionsEvents() {
        return events.evolutionOptions();
    }

    public List<DeathEvent> getDeathEvents() {
        return events.deaths();
    }

    public List<DashEvent> getDashEvents() {
        return events.dashes();
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
    boolean forceKillNow(String playerId) {
        PlayerEntity victim = players.get(playerId);
        if (victim == null || !victim.isAlive()) {
            return false;
        }
        victim.kill();
        clearBiteCooldownsForPlayer(playerId);
        long spawnedAt = playerSpawnMs.getOrDefault(playerId, System.currentTimeMillis());
        events.deaths.add(new DeathEvent(
                playerId, null, "Test", DeathEvent.REASON_EATEN,
                victim.getX(), victim.getY(), 0.0,
                Math.max(0, System.currentTimeMillis() - spawnedAt)));
        return true;
    }

    void submit(GameCommand command) {
        commandQueue.submit(command);
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

        player.setAnimal(target);
        if (target.biome() != Biome.FINAL) {
            SpawnPoint spawnPoint = randomSpawnPointForBiome(target.biome(), target.radius());
            player.setPosition(spawnPoint.x(), spawnPoint.y());
            rebuildSpatialGrid();
        }
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

    /**
     * Whether the given point is inside a drinking-water source: the ocean
     * biome, the river crossing the land, or one of the land/arctic puddles.
     * Aquatic animals refill their drinking-water bar while inside a water
     * source.
     */
    public boolean isInWaterSource(double x, double y) {
        if (biomeAt(x, y) == Biome.OCEAN) {
            return true;
        }
        if (isInRiver(x, y)) {
            return true;
        }
        for (Puddle puddle : puddles) {
            double dx = x - puddle.x();
            double dy = y - puddle.y();
            if (dx * dx + dy * dy <= puddle.radius() * puddle.radius()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the given point falls within the river band that crosses the
     * land biome. The band spans from the land's western edge to the eastern
     * world edge. Coordinates mirror the river rectangle drawn client-side in
     * PixiGame.renderBackground so the visual and the water source line up.
     */
    private boolean isInRiver(double x, double y) {
        double riverTop = height * 0.42;
        double riverBottom = riverTop + RIVER_HEIGHT;
        return x >= width * 0.28 && y >= riverTop && y <= riverBottom;
    }


    /** Read-only view of the water puddles for rendering / snapshots. */
    public List<Puddle> getPuddles() {
        return puddles;
    }


    public SpawnPoint randomSpawnPointForBiome(Biome biome, double radius) {
        Biome targetBiome = biome == Biome.FINAL ? Biome.LAND : biome;

        for (int attempt = 0; attempt < 100; attempt++) {
            SpawnPoint candidate = randomSpawnPointCandidate(targetBiome, radius);
            if (biomeAt(candidate.x(), candidate.y()) == targetBiome) {
                return candidate;
            }
        }

        return fallbackSpawnPointForBiome(targetBiome, radius);
    }

    private SpawnPoint randomSpawnPointCandidate(Biome biome, double radius) {
        double oceanRight = width * 0.28;
        double arcticTop = height * 0.64;

        return switch (biome) {
            case OCEAN -> new SpawnPoint(
                    randomRange(radius, Math.max(radius, oceanRight - radius)),
                    randomRange(radius, height - radius));
            case ARCTIC -> new SpawnPoint(
                    randomRange(Math.min(width - radius, oceanRight + radius), width - radius),
                    randomRange(Math.min(height - radius, arcticTop + radius), height - radius));
            case LAND, FINAL -> new SpawnPoint(
                    randomRange(Math.min(width - radius, oceanRight + radius), width - radius),
                    randomRange(radius, Math.max(radius, arcticTop - radius)));
        };
    }

    private SpawnPoint fallbackSpawnPointForBiome(Biome biome, double radius) {
        double oceanCenterX = width * 0.14;
        double landCenterX = width * 0.64;
        double landCenterY = height * 0.32;
        double arcticCenterY = height * 0.82;

        return switch (biome) {
            case OCEAN -> new SpawnPoint(clamp(oceanCenterX, radius, width - radius), clamp(height * 0.5, radius, height - radius));
            case ARCTIC -> new SpawnPoint(clamp(landCenterX, radius, width - radius), clamp(arcticCenterY, radius, height - radius));
            case LAND, FINAL -> new SpawnPoint(clamp(landCenterX, radius, width - radius), clamp(landCenterY, radius, height - radius));
        };
    }

    public double movementMultiplierAt(PlayerEntity player) {
        return movementMultiplierFor(player.getAnimal(), biomeAt(player.getX(), player.getY()));
    }

    public double movementMultiplierFor(AnimalDefinition animal, Biome currentBiome) {
        if (animal.biome() == Biome.FINAL || currentBiome == Biome.FINAL) {
            return 1.0;
        }
        return animal.biome() == currentBiome ? 1.0 : 0.85;
    }

    private static double randomRange(double min, double max) {
        if (max <= min) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= Math.PI * 2;
        while (angle < -Math.PI) angle += Math.PI * 2;
        return angle;
    }

    public record EvolutionOptionsEvent(
            String playerId,
            List<EvolutionOptionsMessage.EvolutionOption> options
    ) {
    }

    private record BiteResult(boolean killed, double stolenXp) {
        static BiteResult noHit() {
            return new BiteResult(false, 0);
        }
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

    public record SpawnPoint(double x, double y) {
    }

    /**
     * A circular puddle of drinking water on land or arctic terrain. Aquatic
     * animals refill their water bar while standing inside one.
     */
    public record Puddle(double x, double y, double radius) {
    }
}
