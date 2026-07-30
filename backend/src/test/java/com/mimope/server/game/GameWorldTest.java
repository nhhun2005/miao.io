package com.mimope.server.game;

import com.mimope.server.game.data.AnimalDefinition;
import com.mimope.server.game.data.Biome;
import com.mimope.server.game.data.FoodDefinition;
import com.mimope.server.protocol.inbound.InputMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    void puddlesDoNotOverlapTheRiver() {
        double riverTop = HEIGHT * 0.42;
        double riverBottom = riverTop + 150;

        for (GameWorld.Puddle puddle : world.getPuddles()) {
            boolean verticallyClear = puddle.y() + puddle.radius() < riverTop
                    || puddle.y() - puddle.radius() > riverBottom;
            assertTrue(verticallyClear,
                    "puddle at (" + puddle.x() + ", " + puddle.y() + ") overlaps the river");
        }
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
        InputMessage input = new InputMessage(1, 0.0, 1.0, false, 0L);
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
                new InputMessage(1, 0.0, 1.0, false, 0L)));
    }

    @Test
    void queueInputForDeadPlayerIsIgnored() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        player.kill();

        world.queueInput("p1", new InputMessage(1, 0.0, 1.0, false, 0L));
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
        InputMessage input = new InputMessage(1, 0.0, 1.0, false, 0L);
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

        world.queueInput("p1", new InputMessage(1, 0.0, 1.0, false, 0L));
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
    void foodDoesNotDespawnBecauseOfAge() {
        world.tick(0.05);
        Set<String> originalFoodIds = world.getFoods().stream()
                .map(FoodEntity::getInstanceId)
                .collect(Collectors.toSet());

        // Food used to become eligible for random despawn after 1,200 ticks.
        for (int i = 0; i < 1_300; i++) {
            world.tick(0.05);
        }

        Set<String> currentFoodIds = world.getFoods().stream()
                .map(FoodEntity::getInstanceId)
                .collect(Collectors.toSet());
        assertEquals(originalFoodIds, currentFoodIds,
                "Uneaten food must remain until a player consumes it");
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
        assertEquals(3, player.getHealth());
    }

    @Test
    void duplicateEvolutionRequestIsIdempotent() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        player.setAnimal(AnimalDefinition.byId("pig"));
        player.setXp(1_000);

        assertTrue(world.evolvePlayer(player.getId(), "squid").success());
        GameWorld.EvolutionResult duplicate = world.evolvePlayer(player.getId(), "squid");

        assertTrue(duplicate.success());
        assertEquals("squid", player.getAnimal().id());
    }

    @Test
    void everyNormalEvolutionOptionCanBeSelectedAtItsThreshold() {
        for (AnimalDefinition current : AnimalDefinition.all().values()) {
            for (AnimalDefinition target : current.evolutionOptions()) {
                GameWorld isolatedWorld = new GameWorld(WIDTH, HEIGHT, MAX_FOOD);
                PlayerEntity player = isolatedWorld.spawnPlayer(
                        "player-" + current.id() + "-" + target.id(), "Alice");
                player.setAnimal(current);
                player.setXp(target.xpRequired());

                GameWorld.EvolutionResult result =
                        isolatedWorld.evolvePlayer(player.getId(), target.id());

                assertTrue(result.success(),
                        () -> current.id() + " -> " + target.id() + " should be available");
                assertEquals(target.id(), player.getAnimal().id());
            }
        }
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
    void evolvePlayerFullyHealsToNewTierHealth() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        player.setAnimal(AnimalDefinition.byId("fox"));
        for (int i = 0; i < 5; i++) {
            player.damageByBite();
        }
        assertEquals(2, player.getHealth());
        player.addXp(4_000);

        GameWorld.EvolutionResult zebraEvolution = world.evolvePlayer(player.getId(), "zebra");

        assertTrue(zebraEvolution.success());
        assertEquals("zebra", player.getAnimal().id());
        assertEquals(8, player.getHealth());
        assertEquals(8, player.getMaxHealth());

        player.setAnimal(AnimalDefinition.byId("hippo"));
        player.addXp(500_000);

        GameWorld.EvolutionResult dragonEvolution = world.evolvePlayer(player.getId(), "dragon");

        assertTrue(dragonEvolution.success());
        assertEquals("dragon", player.getAnimal().id());
        assertEquals(16, player.getHealth());
        assertEquals(16, player.getMaxHealth());
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
        assertEquals(20, player.getHealth());
        assertEquals(20, player.getMaxHealth());
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
        assertEquals(0.85, world.movementMultiplierFor(AnimalDefinition.byId("shark"), Biome.LAND));
        assertEquals(1.0, world.movementMultiplierFor(AnimalDefinition.byId("mammoth"), Biome.ARCTIC));
        assertEquals(0.85, world.movementMultiplierFor(AnimalDefinition.byId("mammoth"), Biome.OCEAN));
        assertEquals(1.0, world.movementMultiplierFor(AnimalDefinition.byId("blackdragon"), Biome.LAND));
        assertEquals(1.0, world.movementMultiplierFor(AnimalDefinition.byId("blackdragon"), Biome.OCEAN));
        assertEquals(1.0, world.movementMultiplierFor(AnimalDefinition.byId("blackdragon"), Biome.ARCTIC));
    }

    @Test
    void everyCreatureHasAFullWaterBarOnSpawn() {
        PlayerEntity player = world.spawnPlayer("p1", "Mouse");

        assertEquals(100.0, player.getWater(), 0.01);
        assertEquals(100.0, player.getMaxWater(), 0.01);
    }

    @Test
    void creatureInOceanKeepsWaterFull() {
        PlayerEntity player = world.spawnPlayer("p1", "Shrimp", "shrimp");
        setPlayerPosition(player, WIDTH * 0.1, HEIGHT * 0.5);

        world.tick(3.0);

        assertTrue(player.isAlive());
        assertEquals(100.0, player.getWater(), 0.01);
        assertEquals(100.0, player.getMaxWater(), 0.01);
    }

    @Test
    void creatureOnLandDrainsWaterOverTime() {
        PlayerEntity player = world.spawnPlayer("p1", "Mouse");
        setPlayerPosition(player, WIDTH * 0.6, HEIGHT * 0.2);

        world.tick(2.5);

        assertTrue(player.isAlive());
        // Passive thirst drains at 2% of 100 (= 2.0) per second: 100 - 2.5 * 2 = 95.
        assertEquals(95.0, player.getWater(), 0.01);
    }

    @Test
    void beachedSeaCreatureDrainsWaterMuchFasterThanALandAnimal() {
        PlayerEntity shrimp = world.spawnPlayer("p1", "Shrimp", "shrimp");
        setPlayerPosition(shrimp, WIDTH * 0.6, HEIGHT * 0.2);

        world.tick(2.0);

        assertTrue(shrimp.isAlive());
        // Beached: 25% of 100 (= 25.0) per second, so 100 - 2 * 25 = 50.
        assertEquals(50.0, shrimp.getWater(), 0.01);
    }

    @Test
    void beachedSeaCreatureRunsDryInFourSeconds() {
        PlayerEntity shrimp = world.spawnPlayer("p1", "Shrimp", "shrimp");
        setPlayerPosition(shrimp, WIDTH * 0.6, HEIGHT * 0.2);

        world.tick(3.9);
        assertTrue(shrimp.getWater() > 0, "still has water just before the four-second mark");

        world.tick(0.2);
        assertEquals(0.0, shrimp.getWater(), 0.01);
    }

    @Test
    void landAnimalIsUnaffectedByTheBeachedDrain() {
        PlayerEntity mouse = world.spawnPlayer("p1", "Mouse");
        setPlayerPosition(mouse, WIDTH * 0.6, HEIGHT * 0.2);

        world.tick(2.0);

        // Land animals keep the 2%/s passive thirst: 100 - 2 * 2 = 96.
        assertEquals(96.0, mouse.getWater(), 0.01);
    }

    @Test
    void dashingDrainsFivePercentOfWater() {
        PlayerEntity player = world.spawnPlayer("p1", "Mouse");
        setPlayerPosition(player, WIDTH * 0.6, HEIGHT * 0.2);
        // A dash costs a flat 5 water; a 0.05s tick's passive thirst is
        // negligible (0.1), so the bar sits at ~94.9 afterwards.
        world.queueInput("p1", new InputMessage(1, 0.0, 1.0, true, 0L));

        world.tick(0.05);

        assertTrue(player.isAlive());
        assertEquals(94.9, player.getWater(), 0.01);
    }

    @Test
    void creatureDehydratesAndDiesWhenWaterRunsOut() {
        PlayerEntity player = world.spawnPlayer("p1", "Mouse");
        setPlayerPosition(player, WIDTH * 0.6, HEIGHT * 0.2);

        // Empty the 100-point bar (50s at 2.0/sec) then keep draining health
        // in the same tick. Dehydration removes 5% of max HP per second, so a
        // long enough tick both empties the bar and drains all health. Doing
        // it in one tick keeps the death event in the current tick's window.
        world.tick(120.0);


        assertFalse(player.isAlive());
        assertEquals(0.0, player.getWater(), 0.01);
        assertEquals(1, world.getDeathEvents().size());
        assertEquals(DeathEvent.REASON_DEHYDRATION, world.getDeathEvents().get(0).reason());
    }

    @Test
    void returningToOceanRefillsWater() {
        PlayerEntity player = world.spawnPlayer("p1", "Shrimp", "shrimp");
        setPlayerPosition(player, WIDTH * 0.6, HEIGHT * 0.2);

        // Beached sea creatures drain at 25%/s, so two seconds on land halves
        // the bar.
        world.tick(2.0);
        assertEquals(50.0, player.getWater(), 0.01);

        // Back in the ocean the bar refills at 50/sec, so one second tops it up.
        setPlayerPosition(player, WIDTH * 0.1, HEIGHT * 0.5);
        world.tick(1.0);

        assertTrue(player.isAlive());
        assertEquals(100.0, player.getWater(), 0.01);
    }


    @Test
    void standingInLandPuddleRefillsWater() {
        PlayerEntity player = world.spawnPlayer("p1", "Mouse");
        // Start on dry land and drain some water.
        setPlayerPosition(player, WIDTH * 0.6, HEIGHT * 0.2);
        world.tick(4.0);
        assertEquals(92.0, player.getWater(), 0.01);

        // A puddle sits at (0.45, 0.22) on land; standing in it refills water.
        setPlayerPosition(player, WIDTH * 0.45, HEIGHT * 0.22);
        assertTrue(world.isInWaterSource(player.getX(), player.getY()));
        world.tick(1.0);

        assertTrue(player.isAlive());
        // Refill is 50/sec, so one second tops the 92 bar back to 100.
        assertEquals(100.0, player.getWater(), 0.01);
    }

    @Test
    void blackdragonAlsoUsesTheWaterBar() {
        PlayerEntity player = world.spawnPlayer("p1", "Apex");
        player.setAnimal(AnimalDefinition.byId("blackdragon"));
        setPlayerPosition(player, WIDTH * 0.6, HEIGHT * 0.2);

        world.tick(2.5);

        assertTrue(player.isAlive());
        assertEquals(100.0, player.getMaxWater(), 0.01);
        assertEquals(95.0, player.getWater(), 0.01);
    }

    // ------------------------------------------------------------------ predation and abilities

    @Test
    void predatorCollisionBitesPreyForOneHp() {
        PlayerEntity predator = world.spawnPlayer("p1", "Hunter");
        PlayerEntity prey = world.spawnPlayer("p2", "Snack");
        predator.setAnimal(AnimalDefinition.byId("fox"));
        setPlayerPosition(predator, 500, 500);
        setPlayerPosition(prey, 510, 500);

        world.tick(0.05);

        assertTrue(prey.isAlive());
        assertEquals(1, prey.getHealth());
        assertEquals(0, predator.getXp());
        assertTrue(world.getDeathEvents().isEmpty());
    }

    @Test
    void nonLethalBiteStealsTenPercentOfCurrentXp() {
        PlayerEntity attacker = world.spawnPlayer("p1", "Attacker");
        PlayerEntity target = world.spawnPlayer("p2", "Target");
        target.setAnimal(AnimalDefinition.byId("pig"));
        target.setXp(10_000);
        setPlayerPosition(attacker, 500, 500);
        setPlayerPosition(target, 510, 500);
        attacker.setAngle(0);

        world.tick(0.05);

        assertEquals(4, target.getHealth());
        assertEquals(1_000, attacker.getXp());
        assertEquals(9_000, target.getXp());
    }

    @Test
    void nonLethalBiteWithSmallXpStealsAtLeastOneXp() {
        PlayerEntity attacker = world.spawnPlayer("p1", "Attacker");
        PlayerEntity target = world.spawnPlayer("p2", "Target");
        target.setAnimal(AnimalDefinition.byId("pig"));
        target.setXp(5);
        setPlayerPosition(attacker, 500, 500);
        setPlayerPosition(target, 510, 500);
        attacker.setAngle(0);

        world.tick(0.05);

        assertEquals(4, target.getHealth());
        assertEquals(1, attacker.getXp());
        assertEquals(4, target.getXp());
    }

    @Test
    void lowerTierBitesHigherTierAndStealsTenPercentXpWithoutReflectedDamage() {
        PlayerEntity attacker = world.spawnPlayer("p1", "Mouse");
        PlayerEntity target = world.spawnPlayer("p2", "Dragon");
        attacker.setAnimal(AnimalDefinition.byId("mouse"));
        target.setAnimal(AnimalDefinition.byId("dragon"));
        target.setXp(500_000);
        setPlayerPosition(attacker, 500, 500);
        setPlayerPosition(target, 510, 500);
        attacker.setAngle(0);
        target.setAngle(0);

        world.tick(0.05);

        assertTrue(attacker.isAlive());
        assertTrue(target.isAlive());
        assertEquals(2, attacker.getHealth());
        assertEquals(15, target.getHealth());
        assertEquals(50_000, attacker.getXp());
        assertEquals(450_000, target.getXp());
    }

    @Test
    void lowerTierCannotBiteHigherTierFromTheSide() {
        PlayerEntity attacker = world.spawnPlayer("p1", "Mouse");
        PlayerEntity target = world.spawnPlayer("p2", "Dragon");
        target.setAnimal(AnimalDefinition.byId("dragon"));
        setPlayerPosition(attacker, 500, 490);
        setPlayerPosition(target, 500, 500);
        attacker.setAngle(Math.PI / 2.0);
        target.setAngle(0);

        world.tick(0.05);

        assertEquals(target.getMaxHealth(), target.getHealth(),
                "flank contact must not count as a bite to the tail");
        assertEquals(0, attacker.getXp());
    }

    @Test
    void lowerTierMustStayInsideTheNarrowRearCone() {
        PlayerEntity attacker = world.spawnPlayer("p1", "Mouse");
        PlayerEntity target = world.spawnPlayer("p2", "Dragon");
        target.setAnimal(AnimalDefinition.byId("dragon"));
        // 20 degrees away from the exact rear, outside the +/-15 degree cone.
        double offset = Math.toRadians(20);
        setPlayerPosition(target, 500, 500);
        setPlayerPosition(attacker, 500 - Math.cos(offset) * 10, 500 + Math.sin(offset) * 10);
        attacker.setAngle(-offset);
        target.setAngle(0);

        world.tick(0.05);

        assertEquals(target.getMaxHealth(), target.getHealth());
    }

    @Test
    void higherTierBitesLowerTierAndStealsXp() {
        PlayerEntity attacker = world.spawnPlayer("p1", "Dragon");
        PlayerEntity target = world.spawnPlayer("p2", "Mouse");
        attacker.setAnimal(AnimalDefinition.byId("dragon"));
        target.setAnimal(AnimalDefinition.byId("mouse"));
        target.setXp(10);
        setPlayerPosition(attacker, 500, 500);
        setPlayerPosition(target, 510, 500);
        attacker.setAngle(0);
        target.setAngle(0);

        world.tick(0.05);

        assertEquals(16, attacker.getHealth());
        assertEquals(1, target.getHealth());
        assertEquals(1, attacker.getXp());
        assertEquals(9, target.getXp());
    }

    @Test
    void sameTierAnimalsCannotBiteEachOther() {
        PlayerEntity attacker = world.spawnPlayer("p1", "Fox");
        PlayerEntity target = world.spawnPlayer("p2", "Jelly");
        attacker.setAnimal(AnimalDefinition.byId("fox"));
        target.setAnimal(AnimalDefinition.byId("jellyfish"));
        target.setXp(2_500);
        setPlayerPosition(attacker, 500, 500);
        setPlayerPosition(target, 510, 500);
        attacker.setAngle(0);
        target.setAngle(0);

        world.tick(0.05);

        assertEquals(7, attacker.getHealth());
        assertEquals(7, target.getHealth());
        assertEquals(0, attacker.getXp());
        assertEquals(2_500, target.getXp());
        assertTrue(world.getDeathEvents().isEmpty());
    }

    @Test
    void biteCanDropTargetBelowCurrentTierXpUnlockWithoutDeEvolving() {
        PlayerEntity attacker = world.spawnPlayer("p1", "Mouse");
        PlayerEntity target = world.spawnPlayer("p2", "Shark");
        target.setAnimal(AnimalDefinition.byId("shark"));
        target.setXp(126_000);
        setPlayerPosition(attacker, 500, 500);
        setPlayerPosition(target, 510, 500);
        attacker.setAngle(0);

        world.tick(0.05);

        assertEquals(12, target.getHealth());
        assertEquals(12_600, attacker.getXp());
        assertEquals(113_400, target.getXp());
        assertEquals("shark", target.getAnimal().id());
    }

    @Test
    void biteStillDamagesWhenTargetHasZeroXp() {
        PlayerEntity attacker = world.spawnPlayer("p1", "Mouse");
        PlayerEntity target = world.spawnPlayer("p2", "Shark");
        target.setAnimal(AnimalDefinition.byId("shark"));
        target.setXp(0);
        setPlayerPosition(attacker, 500, 500);
        setPlayerPosition(target, 510, 500);
        attacker.setAngle(0);

        world.tick(0.05);

        assertEquals(12, target.getHealth());
        assertEquals(0, attacker.getXp());
        assertEquals(0, target.getXp());
    }

    @Test
    void biteCooldownPreventsEveryTickDamage() {
        PlayerEntity predator = world.spawnPlayer("p1", "Hunter");
        PlayerEntity prey = world.spawnPlayer("p2", "Snack");
        predator.setAnimal(AnimalDefinition.byId("fox"));
        setPlayerPosition(predator, 500, 500);
        setPlayerPosition(prey, 510, 500);

        world.tick(0.05);
        world.tick(0.05);
        world.tick(0.05);

        assertTrue(prey.isAlive());
        assertEquals(1, prey.getHealth());
        assertTrue(world.getDeathEvents().isEmpty());
    }

    @Test
    void lethalBiteTransfersAllRemainingXp() {
        PlayerEntity predator = world.spawnPlayer("p1", "Hunter");
        PlayerEntity prey = world.spawnPlayer("p2", "Snack");
        predator.setAnimal(AnimalDefinition.byId("fox"));
        prey.setXp(9_000);
        prey.damageByBite();
        setPlayerPosition(predator, 500, 500);
        setPlayerPosition(prey, 510, 500);

        world.tick(0.05);

        assertFalse(prey.isAlive());
        assertEquals(0, prey.getHealth());
        assertEquals(9_000, predator.getXp());
        assertEquals(0, prey.getXp());
        assertEquals(1, world.getDeathEvents().size());
        assertEquals(prey.getId(), world.getDeathEvents().get(0).victimId());
        assertEquals(9_000, world.getDeathEvents().get(0).xpAwarded());
    }

    @Test
    void targetBehindAttackerDoesNotTakeBiteDamage() {
        PlayerEntity a = world.spawnPlayer("p1", "Alice");
        PlayerEntity b = world.spawnPlayer("p2", "Bob");
        setPlayerPosition(a, 500, 500);
        setPlayerPosition(b, 490, 500);
        a.setAngle(0);
        b.setAngle(Math.PI);

        world.tick(0.05);

        assertTrue(a.isAlive());
        assertTrue(b.isAlive());
        assertEquals(2, a.getHealth());
        assertEquals(2, b.getHealth());
        assertTrue(world.getDeathEvents().isEmpty());
    }

    @Test
    void lowerTierCannotTradeBitesFaceToFaceWithHigherTier() {
        PlayerEntity a = world.spawnPlayer("p1", "Alice");
        PlayerEntity b = world.spawnPlayer("p2", "Bob");
        a.setAnimal(AnimalDefinition.byId("fox"));
        b.setAnimal(AnimalDefinition.byId("pig"));
        setPlayerPosition(a, 500, 500);
        setPlayerPosition(b, 510, 500);
        a.setAngle(0);
        b.setAngle(Math.PI);

        world.tick(0.05);

        assertEquals(7, a.getHealth());
        assertEquals(4, b.getHealth());
    }

    @Test
    void dashInputCreatesDashEventAndCooldown() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        world.queueInput("p1", new InputMessage(1, 0.0, 1.0, true, 0L));

        world.tick(0.05);

        assertEquals(1, world.getDashEvents().size());
        assertEquals("p1", world.getDashEvents().get(0).playerId());
        assertTrue(player.getDashCooldownRemainingTicks(world.getTick()) > 0);
    }

    @Test
    void movementContinuesAtAConstantRateOnTicksWithoutAFreshInput() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        setPlayerPosition(player, 500, 500);
        world.queueInput("p1", new InputMessage(1, 0.0, 1.0, false, 0L));

        world.tick(0.05);
        double firstStep = player.getX() - 500;

        // No new input arrives for the next two ticks — the client's send timer
        // simply drifted against the tick. The creature must keep travelling the
        // same distance per tick rather than stalling.
        world.tick(0.05);
        double secondStep = player.getX() - 500 - firstStep;
        world.tick(0.05);
        double thirdStep = player.getX() - 500 - firstStep - secondStep;

        assertTrue(firstStep > 0, "player should move on the tick carrying the input");
        assertEquals(firstStep, secondStep, 1e-9);
        assertEquals(firstStep, thirdStep, 1e-9);
    }

    @Test
    void heldOverInputDoesNotRetriggerTheDash() {
        world.spawnPlayer("p1", "Alice");
        world.queueInput("p1", new InputMessage(1, 0.0, 1.0, true, 0L));

        world.tick(0.05);
        assertEquals(1, world.getDashEvents().size());

        // The same input is reused for steering on the following ticks, but dash
        // is single-fire and must not fire again without a fresh dash packet.
        world.tick(0.05);
        assertEquals(0, world.getDashEvents().size());
    }

    @Test
    void dashPushesTheCreatureForwardForSeveralTicks() {
        PlayerEntity player = world.spawnPlayer("p1", "Alice");
        setPlayerPosition(player, 500, 500);

        world.queueInput("p1", new InputMessage(1, 0.0, 1.0, false, 0L));
        world.tick(0.05);
        double normalStep = player.getX() - 500;

        setPlayerPosition(player, 500, 500);
        world.queueInput("p1", new InputMessage(2, 0.0, 1.0, true, 0L));
        world.tick(0.05);
        double dashStep = player.getX() - 500;
        assertEquals(normalStep * 3.0, dashStep, 1e-9, "dash tick moves at 3x the normal speed");

        // The burst spans several ticks, so the push is still active without a
        // fresh dash input.
        double beforeNextTick = player.getX();
        world.tick(0.05);
        assertEquals(normalStep * 3.0, player.getX() - beforeNextTick, 1e-9,
                "dash burst should outlast a single tick");
    }


    @Test
    void biteKnockbacksTargetAwayFromAttacker() {
        PlayerEntity attacker = world.spawnPlayer("p1", "Hunter");
        PlayerEntity target = world.spawnPlayer("p2", "Snack");
        attacker.setAnimal(AnimalDefinition.byId("fox")); // speed 185
        setPlayerPosition(attacker, 500, 500);
        setPlayerPosition(target, 510, 500);
        attacker.setAngle(0);

        world.tick(0.05);

        // dash range = 185 * 3 (DASH_SPEED_MULTIPLIER) * 3 (DASH_DURATION_TICKS) * 0.05 = 83.25
        // knockback = 0.75 * 83.25 = 62.4375, pushed along +x (directly away).
        assertTrue(target.isAlive());
        assertEquals(1, target.getHealth());
        assertEquals(510 + 62.4375, target.getX(), 1e-6, "target shoved away by 0.75x dash range");
        assertEquals(500, target.getY(), 1e-6, "no lateral drift when directly aside");
    }

    @Test
    void rearBiteStillKnockbacksTargetAway() {
        // Attacker faces the target, target faces away (bitten from behind).
        PlayerEntity attacker = world.spawnPlayer("p1", "Hunter");
        PlayerEntity target = world.spawnPlayer("p2", "Snack");
        attacker.setAnimal(AnimalDefinition.byId("fox"));
        setPlayerPosition(attacker, 500, 500);
        setPlayerPosition(target, 510, 500);
        attacker.setAngle(0);
        target.setAngle(0);

        world.tick(0.05);

        assertEquals(510 + 62.4375, target.getX(), 1e-6);
    }

    @Test
    void knockbackIsClampedToWorldBounds() {
        PlayerEntity attacker = world.spawnPlayer("p1", "Hunter");
        PlayerEntity target = world.spawnPlayer("p2", "Snack");
        attacker.setAnimal(AnimalDefinition.byId("fox"));
        double targetRadius = target.getRadius();
        // Place target hard against the right wall so the push would leave the map.
        setPlayerPosition(attacker, WIDTH - 30, 500);
        setPlayerPosition(target, WIDTH - 20, 500);
        attacker.setAngle(0);

        world.tick(0.05);

        assertEquals(WIDTH - targetRadius, target.getX(), 1e-6, "knockback keeps the victim inside the world");
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
