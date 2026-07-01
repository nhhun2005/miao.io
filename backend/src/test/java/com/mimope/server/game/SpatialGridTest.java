package com.mimope.server.game;

import com.mimope.server.game.data.AnimalDefinition;
import com.mimope.server.game.data.FoodDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link SpatialGrid} spatial hash implementation.
 * <p>
 * Verifies that entities are correctly inserted into cells, that queries
 * return the expected nearby entities, and that edge cases like empty
 * grids and out-of-bounds positions are handled gracefully.
 */
class SpatialGridTest {

    private static final double WORLD_WIDTH = 5000;
    private static final double WORLD_HEIGHT = 5000;
    private static final double CELL_SIZE = 200;

    private SpatialGrid grid;

    @BeforeEach
    void setUp() {
        grid = new SpatialGrid(WORLD_WIDTH, WORLD_HEIGHT, CELL_SIZE);
    }

    // ------------------------------------------------------------------ construction

    @Test
    void gridDimensions() {
        assertEquals(25, grid.getCols(), "5000 / 200 = 25 columns");
        assertEquals(25, grid.getRows(), "5000 / 200 = 25 rows");
        assertEquals(CELL_SIZE, grid.getCellSize());
    }

    @Test
    void negativeCellSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpatialGrid(100, 100, -1));
    }

    @Test
    void zeroCellSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpatialGrid(100, 100, 0));
    }

    @Test
    void smallWorldStillHasAtLeastOneCell() {
        SpatialGrid small = new SpatialGrid(10, 10, 200);
        assertEquals(1, small.getCols());
        assertEquals(1, small.getRows());
    }

    // ------------------------------------------------------------------ clear

    @Test
    void clearRemovesAllEntities() {
        PlayerEntity player = createPlayer("p1", 100, 100);
        FoodEntity food = createFood("f1", 200, 200);

        grid.insert(player);
        grid.insert(food);

        assertEquals(1, grid.queryPlayers(100, 100, 1000).size());
        assertEquals(1, grid.queryFoods(200, 200, 1000).size());

        grid.clear();

        assertEquals(0, grid.queryPlayers(100, 100, 1000).size());
        assertEquals(0, grid.queryFoods(200, 200, 1000).size());
    }

    // ------------------------------------------------------------------ player queries

    @Test
    void queryPlayersReturnsNearbyPlayers() {
        PlayerEntity p1 = createPlayer("p1", 100, 100);
        PlayerEntity p2 = createPlayer("p2", 150, 150);
        PlayerEntity p3 = createPlayer("p3", 3000, 3000); // far away

        grid.insert(p1);
        grid.insert(p2);
        grid.insert(p3);

        List<PlayerEntity> nearby = grid.queryPlayers(100, 100, 200);
        assertEquals(2, nearby.size(), "Should find p1 and p2 within 200 units");
        assertTrue(nearby.stream().anyMatch(p -> p.getId().equals("p1")));
        assertTrue(nearby.stream().anyMatch(p -> p.getId().equals("p2")));
    }

    @Test
    void queryPlayersExcludesFarPlayers() {
        PlayerEntity p1 = createPlayer("p1", 100, 100);
        PlayerEntity p2 = createPlayer("p2", 4000, 4000);

        grid.insert(p1);
        grid.insert(p2);

        List<PlayerEntity> nearby = grid.queryPlayers(100, 100, 500);
        assertEquals(1, nearby.size());
        assertEquals("p1", nearby.get(0).getId());
    }

    @Test
    void queryPlayersEmptyGrid() {
        List<PlayerEntity> nearby = grid.queryPlayers(100, 100, 200);
        assertTrue(nearby.isEmpty());
    }

    @Test
    void queryPlayersExactRadiusBoundary() {
        PlayerEntity p1 = createPlayer("p1", 0, 0);
        grid.insert(p1);

        // Query at (200, 0) with radius 200 — p1 at (0,0) is exactly 200 away
        List<PlayerEntity> nearby = grid.queryPlayers(200, 0, 200);
        assertEquals(1, nearby.size(), "Should include player exactly at radius boundary");
    }

    @Test
    void queryPlayersOutOfBoundsPosition() {
        PlayerEntity p1 = createPlayer("p1", 100, 100);
        grid.insert(p1);

        // Query from outside the world — should still work
        List<PlayerEntity> nearby = grid.queryPlayers(-500, -500, 1000);
        assertEquals(1, nearby.size());
    }

    // ------------------------------------------------------------------ food queries

    @Test
    void queryFoodsReturnsNearbyFood() {
        FoodEntity f1 = createFood("f1", 100, 100);
        FoodEntity f2 = createFood("f2", 200, 200);
        FoodEntity f3 = createFood("f3", 4000, 4000);

        grid.insert(f1);
        grid.insert(f2);
        grid.insert(f3);

        List<FoodEntity> nearby = grid.queryFoods(150, 150, 200);
        assertEquals(2, nearby.size());
        assertTrue(nearby.stream().anyMatch(f -> f.getInstanceId().equals("f1")));
        assertTrue(nearby.stream().anyMatch(f -> f.getInstanceId().equals("f2")));
    }

    @Test
    void queryFoodsEmptyGrid() {
        List<FoodEntity> nearby = grid.queryFoods(100, 100, 200);
        assertTrue(nearby.isEmpty());
    }

    // ------------------------------------------------------------------ combined queries

    @Test
    void queryNearbyReturnsBothPlayersAndFood() {
        PlayerEntity p1 = createPlayer("p1", 100, 100);
        FoodEntity f1 = createFood("f1", 150, 150);

        grid.insert(p1);
        grid.insert(f1);

        SpatialGrid.NearbyQueryResult result = grid.queryNearby(100, 100, 200);
        assertEquals(1, result.players().size());
        assertEquals(1, result.foods().size());
    }

    @Test
    void queryNearbyEmpty() {
        SpatialGrid.NearbyQueryResult result = grid.queryNearby(100, 100, 200);
        assertTrue(result.players().isEmpty());
        assertTrue(result.foods().isEmpty());
    }

    // ------------------------------------------------------------------ entity overlap across cells

    @Test
    void largeEntitySpansMultipleCells() {
        // Create a player with a large radius that spans multiple cells
        PlayerEntity large = new PlayerEntity("large", "Big", AnimalDefinition.byId("lion"), 500, 500);
        grid.insert(large);

        // Query from a distance that should still find it due to large radius
        List<PlayerEntity> nearby = grid.queryPlayers(500, 500, 1000);
        assertEquals(1, nearby.size());
    }

    @Test
    void entityAtCellBoundary() {
        // Place entity at a cell boundary (200, 200 is the boundary between cells)
        PlayerEntity p1 = createPlayer("p1", 200, 200);
        grid.insert(p1);

        // Query from adjacent cells should find it
        List<PlayerEntity> fromLeft = grid.queryPlayers(150, 200, 100);
        assertTrue(fromLeft.stream().anyMatch(p -> p.getId().equals("p1")));

        List<PlayerEntity> fromRight = grid.queryPlayers(250, 200, 100);
        assertTrue(fromRight.stream().anyMatch(p -> p.getId().equals("p1")));
    }

    // ------------------------------------------------------------------ helpers

    private static PlayerEntity createPlayer(String id, double x, double y) {
        return new PlayerEntity(id, "TestPlayer", AnimalDefinition.starter(), x, y);
    }

    private static FoodEntity createFood(String instanceId, double x, double y) {
        FoodDefinition berry = FoodDefinition.byId("berry");
        return new FoodEntity(instanceId, berry, x, y);
    }
}
