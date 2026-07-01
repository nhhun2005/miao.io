package com.mimope.server.game;

import com.mimope.server.game.data.AnimalDefinition;
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
}
