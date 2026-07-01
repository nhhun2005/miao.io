package com.mimope.server.game;

import java.util.*;

/**
 * Uniform spatial grid (spatial hash) for efficient nearby-entity queries.
 * <p>
 * Divides the world into square cells of a fixed size. Each entity is inserted
 * into every cell its bounding box overlaps. Queries for nearby entities then
 * only need to check the cells that overlap the query region, reducing the
 * complexity from O(n) to roughly O(1) per query.
 * <p>
 * The grid is rebuilt every tick — it is not persistent between ticks. This
 * keeps the implementation simple and avoids stale-data bugs.
 *
 * <h3>Usage</h3>
 * <pre>
 *   SpatialGrid grid = new SpatialGrid(worldWidth, worldHeight, cellSize);
 *   grid.insert(player);
 *   grid.insert(food);
 *   List<FoodEntity> nearby = grid.queryFoods(player.getX(), player.getY(), radius);
 * </pre>
 */
public class SpatialGrid {

    private final double worldWidth;
    private final double worldHeight;
    private final double cellSize;
    private final int cols;
    private final int rows;

    /** Cells indexed by flat index = row * cols + col. */
    private final List<Cell> cells;

    /**
     * Create a new spatial grid.
     *
     * @param worldWidth  world width in pixels
     * @param worldHeight world height in pixels
     * @param cellSize    cell edge length in pixels (e.g. 200)
     */
    public SpatialGrid(double worldWidth, double worldHeight, double cellSize) {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive");
        }
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.cellSize = cellSize;
        this.cols = Math.max(1, (int) Math.ceil(worldWidth / cellSize));
        this.rows = Math.max(1, (int) Math.ceil(worldHeight / cellSize));
        this.cells = new ArrayList<>(cols * rows);
        for (int i = 0; i < cols * rows; i++) {
            cells.add(new Cell());
        }
    }

    /** Clear all entities from the grid. Called at the start of each tick. */
    public void clear() {
        for (Cell cell : cells) {
            cell.players.clear();
            cell.foods.clear();
        }
    }

    /** Insert a player entity into all cells its bounding circle overlaps. */
    public void insert(PlayerEntity player) {
        double r = player.getRadius();
        double minX = player.getX() - r;
        double minY = player.getY() - r;
        double maxX = player.getX() + r;
        double maxY = player.getY() + r;

        int colMin = clampCol((int) (minX / cellSize));
        int colMax = clampCol((int) (maxX / cellSize));
        int rowMin = clampRow((int) (minY / cellSize));
        int rowMax = clampRow((int) (maxY / cellSize));

        for (int row = rowMin; row <= rowMax; row++) {
            for (int col = colMin; col <= colMax; col++) {
                int idx = row * cols + col;
                cells.get(idx).players.add(player);
            }
        }
    }

    /** Insert a food entity into all cells its bounding circle overlaps. */
    public void insert(FoodEntity food) {
        double r = food.getRadius();
        double minX = food.getX() - r;
        double minY = food.getY() - r;
        double maxX = food.getX() + r;
        double maxY = food.getY() + r;

        int colMin = clampCol((int) (minX / cellSize));
        int colMax = clampCol((int) (maxX / cellSize));
        int rowMin = clampRow((int) (minY / cellSize));
        int rowMax = clampRow((int) (maxY / cellSize));

        for (int row = rowMin; row <= rowMax; row++) {
            for (int col = colMin; col <= colMax; col++) {
                int idx = row * cols + col;
                cells.get(idx).foods.add(food);
            }
        }
    }

    /**
     * Query all players within a circular region.
     *
     * @param cx     circle centre X
     * @param cy     circle centre Y
     * @param radius query radius
     * @return list of players whose centre lies within the circle
     */
    public List<PlayerEntity> queryPlayers(double cx, double cy, double radius) {
        Set<PlayerEntity> result = new LinkedHashSet<>();
        int colMin = clampCol((int) ((cx - radius) / cellSize));
        int colMax = clampCol((int) ((cx + radius) / cellSize));
        int rowMin = clampRow((int) ((cy - radius) / cellSize));
        int rowMax = clampRow((int) ((cy + radius) / cellSize));

        double radiusSq = radius * radius;

        for (int row = rowMin; row <= rowMax; row++) {
            for (int col = colMin; col <= colMax; col++) {
                int idx = row * cols + col;
                for (PlayerEntity p : cells.get(idx).players) {
                    double dx = p.getX() - cx;
                    double dy = p.getY() - cy;
                    if (dx * dx + dy * dy <= radiusSq) {
                        result.add(p);
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * Query all food items within a circular region.
     *
     * @param cx     circle centre X
     * @param cy     circle centre Y
     * @param radius query radius
     * @return list of food items whose centre lies within the circle
     */
    public List<FoodEntity> queryFoods(double cx, double cy, double radius) {
        Set<FoodEntity> result = new LinkedHashSet<>();
        int colMin = clampCol((int) ((cx - radius) / cellSize));
        int colMax = clampCol((int) ((cx + radius) / cellSize));
        int rowMin = clampRow((int) ((cy - radius) / cellSize));
        int rowMax = clampRow((int) ((cy + radius) / cellSize));

        double radiusSq = radius * radius;

        for (int row = rowMin; row <= rowMax; row++) {
            for (int col = colMin; col <= colMax; col++) {
                int idx = row * cols + col;
                for (FoodEntity f : cells.get(idx).foods) {
                    double dx = f.getX() - cx;
                    double dy = f.getY() - cy;
                    if (dx * dx + dy * dy <= radiusSq) {
                        result.add(f);
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * Query all entities (players and food) within a circular region.
     * Returns them in a {@link NearbyQueryResult} for convenience.
     */
    public NearbyQueryResult queryNearby(double cx, double cy, double radius) {
        return new NearbyQueryResult(
                queryPlayers(cx, cy, radius),
                queryFoods(cx, cy, radius)
        );
    }

    // ------------------------------------------------------------------ helpers

    private int clampCol(int col) {
        return Math.max(0, Math.min(cols - 1, col));
    }

    private int clampRow(int row) {
        return Math.max(0, Math.min(rows - 1, row));
    }

    public double getCellSize() {
        return cellSize;
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    /**
     * Return debug information for all non-empty cells.
     * <p>
     * The frontend uses this to draw the Phase 11 spatial-grid overlay when
     * debug mode is enabled. Empty cells are omitted to keep snapshots small.
     */
    public List<com.mimope.server.protocol.outbound.SnapshotMessage.GridCellDebug> getAllCellsDebug() {
        List<com.mimope.server.protocol.outbound.SnapshotMessage.GridCellDebug> result = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Cell cell = cells.get(row * cols + col);
                if (cell.players.isEmpty() && cell.foods.isEmpty()) {
                    continue;
                }

                result.add(new com.mimope.server.protocol.outbound.SnapshotMessage.GridCellDebug(
                        col * cellSize,
                        row * cellSize,
                        Math.min(cellSize, worldWidth - col * cellSize),
                        Math.min(cellSize, worldHeight - row * cellSize),
                        cell.players.size(),
                        cell.foods.size()
                ));
            }
        }

        return result;
    }

    /** Simple holder for query results. */
    public record NearbyQueryResult(
            List<PlayerEntity> players,
            List<FoodEntity> foods
    ) {
    }

    /** One grid cell holding references to overlapping entities. */
    private static class Cell {
        final List<PlayerEntity> players = new ArrayList<>();
        final List<FoodEntity> foods = new ArrayList<>();
    }
}
