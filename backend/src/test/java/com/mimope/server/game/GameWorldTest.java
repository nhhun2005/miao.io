package com.mimope.server.game;

import com.mimope.server.game.data.AnimalDefinition;
import com.mimope.server.game.data.Biome;
import com.mimope.server.game.data.FoodDefinition;
import com.mimope.server.protocol.inbound.InputMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GameWorldTest {

    private static final double WIDTH = 5000;
    private static final double HEIGHT = 5000;
    private static final int MAX_FOOD = 50;

    private GameWorld world;

    @BeforeEach
    void setUp() {
        world = new GameWorld(WIDTH, HEIGHT, MAX_FOOD);
    }

    // ------------------------------------------------------------------ dimensions

    @Test
    void worldDimensions() {
        assertEquals(WIDTH, world.getWidth());
        assertEquals(HEIGHT, world.getHeight());
    }

    @Test
    void initialTickIsZero() {
        assertEquals(0, world.getTick());
    }

    // ------------------------------------------------------------------ players

    @Test
    void spawnPlayerCreatesEntity() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");

        assertNotNull(player);
        assertEquals("p1", player.getId());
        assertEquals("Alice", player.getNickname());
        assertEquals(AnimalDefinition.starter(), player.getAnimal());
        assertTrue(player.isAlive());
        assertEquals(1, world.getPlayerCount());
    }

    @Test
    void spawnPlayerPositionWithinBounds() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");

        double r = AnimalDefinition.starter().radius();
        assertTrue(player.getX() >= r && player.getX() <= WIDTH - r,
                "X should be within [radius, width-radius]");
        assertTrue(player.getY() >= r && player.getY() <= HEIGHT - r,
                "Y should be within [radius, height-radius]");
    }

    @Test
    void spawnPlayerUsesStarterAnimalBiome() {
        PlayerEntity defaultPlayer = world.spawnPlayer("p1", "Default");
        PlayerEntity oceanPlayer = world.spawnPlayer("p2", "Ocean", "shrimp");
        PlayerEntity arcticPlayer = world.spawnPlayer("p3", "Arctic", "chipmunk");

        assertEquals(Biome.LAND, world.biomeAt(defaultPlayer.getX(), defaultPlayer.getY()));
        assertEquals(Biome.OCEAN, world.biomeAt(oceanPlayer.getX(), oceanPlayer.getY()));
        assertEquals(Biome.ARCTIC, world.biomeAt(arcticPlayer.getX(), arcticPlayer.getY()));
    }

    @Test
    void randomSpawnPointForBiomeReturnsPointInsideRequestedBiome() {
        for (Biome biome : java.util.List.of(Biome.LAND, Biome.OCEAN, Biome.ARCTIC)) {
            for (int i = 0; i < 20; i++) {
                GameWorld.SpawnPoint point = world.randomSpawnPointForBiome(biome, 80);

                assertEquals(biome, world.biomeAt(point.x(), point.y()));
                assertTrue(point.x() >= 80 && point.x() <= WIDTH - 80);
                assertTrue(point.y() >= 80 && point.y() <= HEIGHT - 80);
            }
        }
    }

    @Test
    void spawnMultiplePlayers() {
        world.spawnPlayer("p1", "Alice");
        world.spawnPlayer("p2", "Bob");
        world.spawnPlayer("p3", "Charlie");

        assertEquals(3, world.getPlayerCount());
        assertEquals(3, world.getPlayers().size());
    }

    @Test
    void getPlayerById() {
        world.spawnPlayer("p1", "Alice");
        PlayerEntity found = world.getPlayer("p1");
        assertNotNull(found);
        assertEquals("Alice", found.getNickname());

        assertNull(world.getPlayer("nonexistent"));
    }

    @Test
    void removePlayer() {
        world.spawnPlayer("p1", "Alice");
        assertEquals(1, world.getPlayerCount());

        PlayerEntity removed = world.removePlayer("p1");
        assertNotNull(removed);
        assertEquals("Alice", removed.getNickname());
        assertEquals(0, world.getPlayerCount());
    }

    @Test
    void removeNonexistentPlayerReturnsNull() {
        assertNull(world.removePlayer("nonexistent"));
    }

    @Test
    void playersCollectionIsUnmodifiable() {
        world.spawnPlayer("p1", "Alice");
        assertThrows(UnsupportedOperationException.class,
                () -> world.getPlayers().iterator().remove());
    }

    // ------------------------------------------------------------------ input queue

    @Test
    void queueInputForExistingPlayer() {
        world.spawnPlayer("p1", "Alice");
        InputMessage input = new InputMessage(1, 0.0, 1.0, false, false, 0L);
        world.queueInput("p1", input);

        PlayerEntity player = world.getPlayer("p1");
        InputMessage consumed = player.consumeInput();
        assertNotNull(consumed);
        assertEquals(1, consumed.seq());
    }

    @Test
    void queueInputForNonexistentPlayerIsIgnored() {
        // Should not throw
        assertDoesNotThrow(() -> world.queueInput("nonexistent",
                new InputMessage(1, 0.0, 1.0, false, false, 0L)));
    }

    @Test
    void queueInputForDeadPlayerIsIgnored() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        player.kill();

        world.queueInput("p1", new InputMessage(1, 0.0, 1.0, false, false, 0L));
        assertNull(player.consumeInput(), "Dead player should not receive input");
    }

    // ------------------------------------------------------------------ tick

    @Test
    void tickIncrementsTick() {
        world.tick(0.05);
        assertEquals(1, world.getTick());
        world.tick(0.05);
        assertEquals(2, world.getTick());
    }

    @Test
    void tickAppliesPlayerMovement() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        setPlayerPosition(player, 2500, 2500);
        double startX = player.getX();

        // Queue input to move right
        InputMessage input = new InputMessage(1, 0.0, 1.0, false, false, 0L);
        world.queueInput("p1", input);

        world.tick(0.05); // 0.05s at speed 200 = 10 units

        assertEquals(startX + 10, player.getX(), 0.5, "Player should move right");
    }

    @Test
    void tickDoesNotMovePlayerWithoutInput() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        double startX = player.getX();
        double startY = player.getY();

        world.tick(0.05);

        assertEquals(startX, player.getX(), 0.01);
        assertEquals(startY, player.getY(), 0.01);
    }

    @Test
    void tickSkipsDeadPlayers() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        double startX = player.getX();
        player.kill();

        world.queueInput("p1", new InputMessage(1, 0.0, 1.0, false, false, 0L));
        world.tick(0.05);

        assertEquals(startX, player.getX(), 0.01, "Dead player should not move");
    }

    // ------------------------------------------------------------------ food

    @Test
    void tickReplenishesFood() {
        assertEquals(0, world.getFoodCount());
        world.tick(0.05);
        assertEquals(MAX_FOOD, world.getFoodCount(), "Should replenish to max food count");
    }

    @Test
    void visibleEntitiesIncludeReplenishedFoodAfterSameTick() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");

        world.tick(0.05);

        SpatialGrid.NearbyQueryResult visible = world.getVisibleEntities(player.getId(), WIDTH);
        assertFalse(visible.foods().isEmpty(),
                "Food spawned during a tick should be visible in snapshots sent after that tick");
    }

    @Test
    void foodPositionsWithinBounds() {
        world.tick(0.05);
        for (FoodEntity food : world.getFoods()) {
            assertTrue(food.getX() > 0 && food.getX() < WIDTH,
                    "Food X should be within world bounds");
            assertTrue(food.getY() > 0 && food.getY() < HEIGHT,
                    "Food Y should be within world bounds");
        }
    }

    @Test
    void removeFoodById() {
        world.tick(0.05); // Spawn food
        assertTrue(world.getFoodCount() > 0);

        FoodEntity first = world.getFoods().iterator().next();
        FoodEntity removed = world.removeFood(first.getInstanceId());

        assertNotNull(removed);
        assertEquals(first.getInstanceId(), removed.getInstanceId());
        assertEquals(MAX_FOOD - 1, world.getFoodCount());
    }

    @Test
    void removeNonexistentFoodReturnsNull() {
        assertNull(world.removeFood("nonexistent"));
    }

    @Test
    void foodReplenishesAfterRemoval() {
        world.tick(0.05); // Fill to MAX_FOOD
        assertEquals(MAX_FOOD, world.getFoodCount());

        // Remove one food
        FoodEntity first = world.getFoods().iterator().next();
        world.removeFood(first.getInstanceId());
        assertEquals(MAX_FOOD - 1, world.getFoodCount());

        // Next tick should replenish
        world.tick(0.05);
        assertEquals(MAX_FOOD, world.getFoodCount());
    }

    @Test
    void foodsCollectionIsUnmodifiable() {
        world.tick(0.05);
        assertThrows(UnsupportedOperationException.class,
                () -> world.getFoods().iterator().remove());
    }

    @Test
    void edibleFoodCollisionAwardsXpAndRemovesFood() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        FoodDefinition berry = FoodDefinition.byId("berry");
        FoodEntity food = addFoodAt("test-food", berry, player.getX(), player.getY());

        world.tick(0.05);

        assertEquals(berry.xp(), player.getXp());
        assertNull(world.removeFood(food.getInstanceId()), "Collected food should be removed");
        assertEquals(1, world.getFoodPickupEvents().size());

        FoodPickupEvent event = world.getFoodPickupEvents().get(0);
        assertEquals(food.getInstanceId(), event.foodInstanceId());
        assertEquals(berry.id(), event.foodId());
        assertEquals(player.getId(), event.playerId());
        assertEquals(berry.xp(), event.xp());
    }

    @Test
    void foodCollisionIgnoresTierLockedFood() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        FoodDefinition meat = FoodDefinition.byId("meat");
        addFoodAt("test-food", meat, player.getX(), player.getY());

        world.tick(0.05);

        assertEquals(0, player.getXp());
        assertTrue(world.getFoods().stream()
                .anyMatch(food -> food.getInstanceId().equals("test-food")));
        assertTrue(world.getFoodPickupEvents().isEmpty());
    }

    @Test
    void foodPickupEventsClearEachTick() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        FoodDefinition berry = FoodDefinition.byId("berry");
        addFoodAt("test-food", berry, player.getX(), player.getY());

        world.tick(0.05);
        assertEquals(1, world.getFoodPickupEvents().size());

        world.tick(0.05);
        assertTrue(world.getFoodPickupEvents().isEmpty());
    }

    // ------------------------------------------------------------------ evolution

    @Test
    void tickEmitsEvolutionOptionsWhenThresholdReached() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        player.addXp(50);

        world.tick(0.05);

        assertEquals(1, world.getEvolutionOptionsEvents().size());
        GameWorld.EvolutionOptionsEvent event = world.getEvolutionOptionsEvents().get(0);
        assertEquals(player.getId(), event.playerId());
        assertEquals("rabbit", event.options().get(0).animalId());
    }

    @Test
    void evolutionOptionsAreOnlySentOnceUntilEvolution() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        player.addXp(50);

        world.tick(0.05);
        assertEquals(1, world.getEvolutionOptionsEvents().size());

        world.tick(0.05);
        assertTrue(world.getEvolutionOptionsEvents().isEmpty());
    }

    @Test
    void evolvePlayerValidatesThresholdAndPath() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");

        GameWorld.EvolutionResult tooEarly = world.evolvePlayer(player.getId(), "rabbit");
        assertFalse(tooEarly.success());
        assertEquals("mouse", player.getAnimal().id());

        player.addXp(50);
        GameWorld.EvolutionResult evolved = world.evolvePlayer(player.getId(), "rabbit");
        assertTrue(evolved.success());
        assertEquals("rabbit", player.getAnimal().id());
        assertEquals(AnimalDefinition.byId("rabbit").maxHealth(), player.getHealth());
    }

    @Test
    void evolvePlayerRelocatesToTargetAnimalBiome() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice", "shrimp");
        player.addXp(50);

        GameWorld.EvolutionResult landEvolution = world.evolvePlayer(player.getId(), "rabbit");

        assertTrue(landEvolution.success());
        assertEquals("rabbit", player.getAnimal().id());
        assertEquals(Biome.LAND, world.biomeAt(player.getX(), player.getY()));

        player.setAnimal(AnimalDefinition.byId("trout"));
        player.addXp(200);
        player.setPosition(WIDTH * 0.6, HEIGHT * 0.2);

        GameWorld.EvolutionResult arcticEvolution = world.evolvePlayer(player.getId(), "penguin");

        assertTrue(arcticEvolution.success());
        assertEquals("penguin", player.getAnimal().id());
        assertEquals(Biome.ARCTIC, world.biomeAt(player.getX(), player.getY()));
    }

    @Test
    void evolvePlayerRejectsSkippedTier() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        player.addXp(500);

        GameWorld.EvolutionResult result = world.evolvePlayer(player.getId(), "fox");

        assertFalse(result.success());
        assertEquals("mouse", player.getAnimal().id());
    }

    @Test
    void finalTierEvolutionOptionsMatchPlan() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        player.setAnimal(AnimalDefinition.byId("hippo"));
        player.addXp(500_000);

        assertEquals(
                java.util.List.of("dragon", "kraken", "yeti"),
                player.getAvailableEvolutionOptions().stream().map(AnimalDefinition::id).toList());
    }

    @Test
    void blackdragonRequiresApexAnimalAndFinalXp() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        player.setAnimal(AnimalDefinition.byId("dragon"));
        player.addXp(999_999);

        assertFalse(world.evolvePlayer(player.getId(), "blackdragon").success());
        assertEquals("dragon", player.getAnimal().id());

        player.addXp(1);
        GameWorld.EvolutionResult result = world.evolvePlayer(player.getId(), "blackdragon");

        assertTrue(result.success());
        assertEquals("blackdragon", player.getAnimal().id());
    }

    @Test
    void blackdragonDoesNotForceBiomeRelocation() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        player.setAnimal(AnimalDefinition.byId("dragon"));
        player.addXp(1_000_000);
        player.setPosition(WIDTH * 0.1, HEIGHT * 0.8);
        double x = player.getX();
        double y = player.getY();

        GameWorld.EvolutionResult result = world.evolvePlayer(player.getId(), "blackdragon");

        assertTrue(result.success());
        assertEquals("blackdragon", player.getAnimal().id());
        assertEquals(x, player.getX());
        assertEquals(y, player.getY());
        assertEquals(Biome.OCEAN, world.biomeAt(player.getX(), player.getY()));
    }

    @Test
    void movementMultiplierComparesAnimalLaneWithCurrentBiome() {
        assertEquals(1.0, world.movementMultiplierFor(AnimalDefinition.byId("shark"), Biome.OCEAN));
        assertEquals(0.75, world.movementMultiplierFor(AnimalDefinition.byId("shark"), Biome.LAND));
        assertEquals(1.0, world.movementMultiplierFor(AnimalDefinition.byId("mammoth"), Biome.ARCTIC));
        assertEquals(0.75, world.movementMultiplierFor(AnimalDefinition.byId("mammoth"), Biome.OCEAN));
        assertEquals(1.0, world.movementMultiplierFor(AnimalDefinition.byId("blackdragon"), Biome.LAND));
        assertEquals(1.0, world.movementMultiplierFor(AnimalDefinition.byId("blackdragon"), Biome.OCEAN));
        assertEquals(1.0, world.movementMultiplierFor(AnimalDefinition.byId("blackdragon"), Biome.ARCTIC));
    }

    @Test
    void oceanAnimalInOceanKeepsOceanSurvivalFull() {
        PlayerEntity player = world.spawnPlayer("p1", "Shrimp", "shrimp");
        setPlayerPosition(player, WIDTH * 0.1, HEIGHT * 0.5);

        world.tick(3.0);

        assertTrue(player.isAlive());
        assertEquals(10.0, player.getOceanSurvival(), 0.01);
        assertEquals(10.0, player.getMaxOceanSurvival(), 0.01);
    }

    @Test
    void oceanAnimalOutsideOceanDrainsOceanSurvival() {
        PlayerEntity player = world.spawnPlayer("p1", "Shrimp", "shrimp");
        setPlayerPosition(player, WIDTH * 0.6, HEIGHT * 0.2);

        world.tick(2.5);

        assertTrue(player.isAlive());
        assertEquals(7.5, player.getOceanSurvival(), 0.01);
    }

    @Test
    void oceanAnimalDiesWhenOceanSurvivalReachesZero() {
        PlayerEntity player = world.spawnPlayer("p1", "Shrimp", "shrimp");
        setPlayerPosition(player, WIDTH * 0.6, HEIGHT * 0.2);

        world.tick(10.1);

        assertFalse(player.isAlive());
        assertEquals(0.0, player.getOceanSurvival(), 0.01);
        assertEquals(1, world.getDeathEvents().size());
        assertEquals(DeathEvent.REASON_OCEAN_SURVIVAL, world.getDeathEvents().get(0).reason());
    }

    @Test
    void oceanAnimalReturningToOceanRefillsOceanSurvival() {
        PlayerEntity player = world.spawnPlayer("p1", "Shrimp", "shrimp");
        setPlayerPosition(player, WIDTH * 0.6, HEIGHT * 0.2);

        world.tick(4.0);
        assertEquals(6.0, player.getOceanSurvival(), 0.01);

        setPlayerPosition(player, WIDTH * 0.1, HEIGHT * 0.5);
        world.tick(0.05);

        assertTrue(player.isAlive());
        assertEquals(10.0, player.getOceanSurvival(), 0.01);
    }

    @Test
    void landAnimalOutsideOceanDoesNotUseOceanSurvivalDeath() {
        PlayerEntity player = world.spawnPlayer("p1", "Mouse");
        setPlayerPosition(player, WIDTH * 0.6, HEIGHT * 0.2);

        world.tick(20.0);

        assertTrue(player.isAlive());
        assertEquals(0.0, player.getMaxOceanSurvival(), 0.01);
        assertTrue(world.getDeathEvents().isEmpty());
    }

    @Test
    void blackdragonDoesNotUseOceanSurvivalDeath() {
        PlayerEntity player = world.spawnPlayer("p1", "Apex");
        player.setAnimal(AnimalDefinition.byId("blackdragon"));
        setPlayerPosition(player, WIDTH * 0.6, HEIGHT * 0.2);

        world.tick(20.0);

        assertTrue(player.isAlive());
        assertEquals(0.0, player.getMaxOceanSurvival(), 0.01);
        assertTrue(world.getDeathEvents().isEmpty());
    }

    @Test
    void winterSkinRollUsesAnimalEligibilityAndThreshold() {
        assertEquals("shark_winter", world.rollSkinId(AnimalDefinition.byId("shark"), 0.49));
        assertEquals("shark", world.rollSkinId(AnimalDefinition.byId("shark"), 0.50));
        assertEquals("chipmunk", world.rollSkinId(AnimalDefinition.byId("chipmunk"), 0.10));
    }

    // ------------------------------------------------------------------ predation and abilities

    @Test
    void predatorCollisionKillsPreyAndAwardsXp() {
        PlayerEntity predator = world.spawnPlayer("p1", "Hunter");
        PlayerEntity prey = world.spawnPlayer("p2", "Snack");
        predator.setAnimal(AnimalDefinition.byId("fox"));
        setPlayerPosition(predator, 500, 500);
        setPlayerPosition(prey, 510, 500);

        world.tick(0.05);

        assertFalse(prey.isAlive());
        assertTrue(predator.getXp() >= 50);
        assertEquals(1, world.getDeathEvents().size());
        assertEquals(prey.getId(), world.getDeathEvents().get(0).victimId());
    }

    @Test
    void sameTierCollisionDoesNotKill() {
        PlayerEntity a = world.spawnPlayer("p1", "Alice");
        PlayerEntity b = world.spawnPlayer("p2", "Bob");
        setPlayerPosition(a, 500, 500);
        setPlayerPosition(b, 510, 500);

        world.tick(0.05);

        assertTrue(a.isAlive());
        assertTrue(b.isAlive());
        assertTrue(world.getDeathEvents().isEmpty());
    }

    @Test
    void abilityInputCreatesDashEventAndCooldown() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        world.queueInput("p1", new InputMessage(1, 0.0, 1.0, false, true, 0L));

        world.tick(0.05);

        assertEquals(1, world.getAbilityEvents().size());
        assertEquals("dash", world.getAbilityEvents().get(0).abilityId());
        assertTrue(player.getAbilityCooldownRemainingTicks(world.getTick()) > 0);
    }

    @SuppressWarnings("unchecked")
    private FoodEntity addFoodAt(String instanceId, FoodDefinition definition, double x, double y) {
        try {
            Field foodsField = GameWorld.class.getDeclaredField("foods");
            foodsField.setAccessible(true);
            Map<String, FoodEntity> foods = (Map<String, FoodEntity>) foodsField.get(world);
            FoodEntity food = new FoodEntity(instanceId, definition, x, y);
            foods.put(instanceId, food);
            return food;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to seed test food", ex);
        }
    }

    private void setPlayerPosition(PlayerEntity player, double x, double y) {
        player.setPosition(x, y);
    }
}
