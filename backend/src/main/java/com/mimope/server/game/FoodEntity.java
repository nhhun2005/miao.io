package com.mimope.server.game;

import com.mimope.server.game.data.FoodDefinition;

/**
 * Immutable entity representing a single food item in the {@link GameWorld}.
 * <p>
 * Each food entity has a unique instance ID (distinct from the food-type ID),
 * a position, and a reference to its {@link FoodDefinition}.
 */
public class FoodEntity {

    private final String instanceId;
    private final FoodDefinition definition;
    private final double x;
    private final double y;

    public FoodEntity(String instanceId, FoodDefinition definition, double x, double y) {
        this.instanceId = instanceId;
        this.definition = definition;
        this.x = x;
        this.y = y;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public FoodDefinition getDefinition() {
        return definition;
    }

    public String getFoodId() {
        return definition.id();
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getRadius() {
        return definition.radius();
    }

    public int getXp() {
        return definition.xp();
    }

    public int getMinTier() {
        return definition.minTier();
    }

    @Override
    public String toString() {
        return "FoodEntity{id='" + instanceId + "', type=" + definition.id()
                + ", pos=(" + x + "," + y + ")}";
    }
}
