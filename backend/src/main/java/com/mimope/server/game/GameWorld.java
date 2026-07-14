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
    private static final long BITE_COOLDOWN_TICKS = 20;
    private static final double BITE_ARC_RADIANS = Math.PI * 2.0 / 3.0;

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
                new Puddle(width * 0.86, height * 0.46, 210),
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
                double biomeMultiplier = movementMultiplierFor(player.getAnimal(), biomeAt(player.getX(), player.getY()));
                player.applyMovement(input, deltaTime, width, height, abilityMultiplier * biomeMultiplier);
            }
        }

        // 2. Update every creature's drinking-water bar (refill in water,
        // dehydrate when empty on land).
        checkWater(deltaTime);

        // 3. Rebuild spatial grid with current entity positions for collision queries.
        rebuildSpatialGrid();

        // 3. Check food collisions using spatial grid — award XP, remove collected food
        checkFoodCollisionsSpatial();

        // 4. Resolve player-vs-player predation.
        checkPlayerPredation();

        // 5. Send evolution options once a player has enough XP for the next tier.
        checkEvolutionOptions();

        // 4. Despawn stale food (protecting items near players so they don't
        // vanish just as someone approaches to eat them).
        foodSpawnService.despawnStaleFood(foods, tick, players.values());

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
     * Update every creature's drinking-water bar. Standing in a water source
     * refills it; when empty on land the creature dehydrates and loses health.
     * A death from dehydration emits a death event.
     */
    private void checkWater(double deltaTime) {
        for (PlayerEntity player : players.values()) {
            if (!player.isAlive()) {
                continue;
            }

            player.updateWater(isInWaterSource(player.getX(), player.getY()), deltaTime);

            if (!player.isAlive()) {
                long spawnedAt = playerSpawnMs.getOrDefault(player.getId(), System.currentTimeMillis());
                deathEvents.add(new DeathEvent(
                        player.getId(),
                        null,
                        null,
                        DeathEvent.REASON_DEHYDRATION,
                        player.getX(),
                        player.getY(),
                        0.0,
                        Math.max(0, System.currentTimeMillis() - spawnedAt)
                ));
            }
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
                    // Eating also tops up the creature's drinking water.
                    player.refillWaterOnFood();

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
                BiteResult result = applyBite(attacker, target);
                if (result.killed()) {
                    killedThisTick.add(target.getId());
                }
            }
        }
    }

    private BiteResult applyBite(PlayerEntity attacker, PlayerEntity target) {
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
        if (!canBiteNow(attacker.getId(), target.getId())) {
            return BiteResult.noHit();
        }

        boolean lethal = target.getHealth() <= 1;
        double stolenXp = transferXpOnBite(attacker, target, lethal);
        target.damageByBite();
        log.debug("{} bit {} (-1hp, stolenXp={}, health={}/{})",
                attacker.getNickname(), target.getNickname(), stolenXp, target.getHealth(), target.getMaxHealth());

        if (!target.isDeadByHealth()) {
            return new BiteResult(false, stolenXp);
        }

        target.kill();
        clearBiteCooldownsForPlayer(target.getId());
        long spawnedAt = playerSpawnMs.getOrDefault(target.getId(), System.currentTimeMillis());
        deathEvents.add(new DeathEvent(
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
        player.setAnimal(starter, rollSkinId(starter));
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
            clearBiteCooldownsForPlayer(playerId);
            long spawnedAt = playerSpawnMs.getOrDefault(playerId, System.currentTimeMillis());
            deathEvents.add(new DeathEvent(
                    playerId,
                    null,
                    "Test",
                    DeathEvent.REASON_EATEN,
                    victim.getX(),
                    victim.getY(),
                    0.0,
                    Math.max(0, System.currentTimeMillis() - spawnedAt)
            ));
        }
    }

    /**
     * Debug helper: instantly level the player up to the next tier so testers
     * can quickly walk through the evolution chain. Grants enough XP to satisfy
     * the next evolution option's requirement, then evolves into it. Picks the
     * first available option for the next tier (matching the player's biome when
     * possible).
     */
    public EvolutionResult debugLevelUp(String playerId) {
        PlayerEntity player = players.get(playerId);
        if (player == null || !player.isAlive()) {
            return EvolutionResult.failure("Player is not alive.");
        }

        AnimalDefinition current = player.getAnimal();
        List<AnimalDefinition> options = current.evolutionOptions();
        if (options.isEmpty()) {
            // Already at the top of the normal chain; try the final form.
            AnimalDefinition finalForm = AnimalDefinition.byId("blackdragon");
            if (finalForm == null || player.getAnimal().biome() == Biome.FINAL) {
                return EvolutionResult.failure("Already at the maximum tier.");
            }
            player.addXp(Math.max(0, finalForm.xpRequired() - player.getXp()));
            if (!player.canEvolveTo(finalForm)) {
                return EvolutionResult.failure("Final evolution is not available for this animal.");
            }
            return evolvePlayer(playerId, finalForm.id());
        }

        AnimalDefinition target = options.stream()
                .filter(a -> a.biome() == current.biome())
                .findFirst()
                .orElse(options.get(0));

        player.addXp(Math.max(0, target.xpRequired() - player.getXp()));
        return evolvePlayer(playerId, target.id());
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
     * Whether the given point is inside a drinking-water source: either the
     * ocean biome or one of the land/arctic puddles. Aquatic animals refill
     * their drinking-water bar while inside a water source.
     */
    public boolean isInWaterSource(double x, double y) {
        if (biomeAt(x, y) == Biome.OCEAN) {
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
        return animal.biome() == currentBiome ? 1.0 : 0.75;
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
        return rollSkinId(animal, ThreadLocalRandom.current().nextDouble());
    }

    String rollSkinId(AnimalDefinition animal, double roll) {
        if (animal.hasWinterSkin() && roll < 0.5) {
            return animal.id() + "_winter";
        }
        return animal.id();
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
