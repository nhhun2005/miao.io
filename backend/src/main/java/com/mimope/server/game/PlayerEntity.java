package com.mimope.server.game;

import com.mimope.server.game.data.AnimalDefinition;
import com.mimope.server.protocol.inbound.InputMessage;

/**
 * Mutable entity representing a single player in the {@link GameWorld}.
 * <p>
 * Holds position, velocity, dimensions, health, XP, and the latest
 * queued input. Updated once per tick by the {@link GameLoop}.
 */
public class PlayerEntity {

    private final String id;
    private String nickname;
    private AnimalDefinition animal;
    private String skinId;

    // Position & motion
    private double x;
    private double y;
    private double angle;

    // Stats
    private double health;
    private double xp;

    /**
     * Drinking-water reserve carried by every creature. Boosting drains it,
     * standing in a water source or eating food refills it, and running dry
     * disables boosting while slowly draining health (dehydration).
     * <p>
     * The wire/protocol field is still named {@code oceanSurvival} for
     * backwards compatibility; semantically it is now a universal water bar.
     */
    private double water;
    private double maxWater;

    /** Maximum drinking-water capacity. */
    private static final double MAX_WATER = 100.0;

    /**
     * Water drained per second at all times while out of a water source
     * (thirst over time, 2% of the maximum per second — empties in ~50s).
     */
    private static final double WATER_DRAIN_PER_SECOND_PASSIVE = MAX_WATER * 0.02;

    /**
     * Extra water drained per second while boosting (25% of the maximum),
     * added on top of the passive drain so boosting always drains faster
     * than simply moving.
     */
    private static final double WATER_DRAIN_PER_SECOND_BOOSTING = MAX_WATER * 0.25;

    /** Water refilled per second while standing in a water source. */
    private static final double WATER_REFILL_PER_SECOND = MAX_WATER * 0.5;

    /** Water restored when eating a food item. */
    private static final double WATER_RESTORED_PER_FOOD = 15.0;

    /**
     * Fraction of maximum health drained per second while completely out of
     * water (dehydration): 5% of max HP per second.
     */
    private static final double DEHYDRATION_HP_FRACTION_PER_SECOND = 0.05;


    // Latest queued input (set by the WebSocket handler, consumed by the tick)
    private volatile InputMessage pendingInput;

    // Lifecycle
    private boolean alive = true;

    // Evolution
    private boolean evolutionOptionsSent = false;

    // Ability: first MVP ability is a short dash.
    private static final long ABILITY_COOLDOWN_TICKS = 100;
    private long lastAbilityTick = -ABILITY_COOLDOWN_TICKS;
    private long guardUntilTick = -1;

    public PlayerEntity(String id, String nickname, AnimalDefinition animal, double x, double y) {
        this.id = id;
        this.nickname = nickname;
        this.animal = animal;
        this.skinId = animal.id();
        this.x = x;
        this.y = y;
        this.angle = 0;
        this.health = animal.maxHealth();
        this.xp = 0;
        resetWater();
    }

    // ------------------------------------------------------------------ input queue

    /**
     * Queue an input message. Only the most recent input is kept;
     * older unprocessed inputs are discarded (latest-wins strategy).
     */
    public void queueInput(InputMessage input) {
        this.pendingInput = input;
    }

    /**
     * Consume and clear the pending input. Returns {@code null} if none.
     */
    public InputMessage consumeInput() {
        InputMessage input = this.pendingInput;
        this.pendingInput = null;
        return input;
    }

    // ------------------------------------------------------------------ movement

    /**
     * Apply one tick of movement based on the given input.
     *
     * @param input     the player input (angle, intensity, boost)
     * @param deltaTime seconds elapsed this tick
     * @param worldWidth  world width for clamping
     * @param worldHeight world height for clamping
     */
    public void applyMovement(InputMessage input, double deltaTime, double worldWidth, double worldHeight) {
        applyMovement(input, deltaTime, worldWidth, worldHeight, 1.0);
    }

    public void applyMovement(InputMessage input,
                              double deltaTime,
                              double worldWidth,
                              double worldHeight,
                              double speedMultiplier) {
        double speed = animal.speed();
        double intensity = input.intensity();

        // Boost: 50% speed increase, at the cost of drinking water.
        // Boosting is only possible while the water bar is above zero and
        // drains it at 25% of the maximum per second.
        if (input.boost() && water > 0) {
            speed *= 1.5;
            consumeBoostWater(deltaTime);
        }
        speed *= speedMultiplier;

        double moveAngle = input.angle();
        double dx = Math.cos(moveAngle) * speed * intensity * deltaTime;
        double dy = Math.sin(moveAngle) * speed * intensity * deltaTime;

        this.x += dx;
        this.y += dy;
        this.angle = moveAngle;

        // Clamp to world bounds (keep player radius inside)
        double r = animal.radius();
        this.x = Math.max(r, Math.min(worldWidth - r, this.x));
        this.y = Math.max(r, Math.min(worldHeight - r, this.y));
    }

    /**
     * Drain the drinking-water bar as the cost of boosting. Removes
     * {@code 25%} of the maximum per second, scaled by the tick's elapsed
     * time. Water never drops below zero.
     *
     * @param deltaTime seconds elapsed this tick
     */
    private void consumeBoostWater(double deltaTime) {
        if (water <= 0 || deltaTime <= 0) {
            return;
        }
        this.water = Math.max(0, this.water - WATER_DRAIN_PER_SECOND_BOOSTING * deltaTime);
    }

    /**
     * Whether the creature currently has enough water to boost.
     */
    public boolean canBoost() {
        return water > 0;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }

    // ------------------------------------------------------------------ state changes

    public void addXp(double amount) {
        this.xp += amount;
    }

    public void setXp(double xp) {
        this.xp = Math.max(0, xp);
    }

    public void damage(double amount) {
        damage(amount, -1);
    }

    public void damage(double amount, long currentTick) {
        if (currentTick >= 0 && currentTick <= guardUntilTick) {
            amount *= 0.5;
        }
        this.health = Math.max(0, this.health - amount);
        if (this.health <= 0) {
            kill();
        }
    }

    public void damageByBite() {
        this.health = Math.max(0, this.health - 1);
    }

    public boolean isDeadByHealth() {
        return health <= 0;
    }

    public void setAnimal(AnimalDefinition animal) {
        setAnimal(animal, animal.id());
    }

    public void setAnimal(AnimalDefinition animal, String skinId) {
        this.animal = animal;
        this.skinId = skinId;
        this.health = animal.maxHealth();
        this.evolutionOptionsSent = false;
        resetWater();
    }

    /**
     * Update the drinking-water bar each tick.
     * <p>
     * While inside a water source (the ocean or a puddle) the bar refills at
     * {@code 50%} of the maximum per second. Otherwise the bar drains over
     * time (thirst); when it reaches zero the creature dehydrates and loses
     * {@code 5%} of its maximum health per second. Reaching zero health is
     * fatal.
     *
     * @param inWaterSource {@code true} if the creature is currently in the
     *                      ocean or standing in a puddle
     * @param deltaTime     seconds elapsed this tick
     */
    public void updateWater(boolean inWaterSource, double deltaTime) {
        if (deltaTime <= 0) {
            return;
        }

        if (inWaterSource) {
            this.water = Math.min(this.maxWater, this.water + WATER_REFILL_PER_SECOND * deltaTime);
            return;
        }

        // Passive thirst: the water bar drains over time whenever the creature
        // is not standing in a water source.
        this.water = Math.max(0, this.water - WATER_DRAIN_PER_SECOND_PASSIVE * deltaTime);

        if (this.water <= 0) {
            // Dehydration: no water left, drain 5% of max health per second.
            double dehydrationDamage = getMaxHealth() * DEHYDRATION_HP_FRACTION_PER_SECOND * deltaTime;
            this.health = Math.max(0, this.health - dehydrationDamage);
            if (this.health <= 0) {
                kill();
            }
        }
    }


    /**
     * Restore drinking water when eating a food item. The bar never exceeds
     * its maximum.
     */
    public void refillWaterOnFood() {
        this.water = Math.min(this.maxWater, this.water + WATER_RESTORED_PER_FOOD);
    }

    private void resetWater() {
        this.maxWater = MAX_WATER;
        this.water = MAX_WATER;
    }

    public boolean canEvolveTo(AnimalDefinition target) {
        if (target == null) {
            return false;
        }
        if ("blackdragon".equals(target.id())) {
            return animal.canUnlockFinal(xp);
        }
        return animal.evolutionOptions().stream().anyMatch(option -> option.id().equals(target.id()))
                && xp >= target.xpRequired();
    }

    public boolean shouldSendEvolutionOptions() {
        return !evolutionOptionsSent && !getAvailableEvolutionOptions().isEmpty();
    }

    public void markEvolutionOptionsSent() {
        this.evolutionOptionsSent = true;
    }

    public java.util.List<AnimalDefinition> getAvailableEvolutionOptions() {
        java.util.List<AnimalDefinition> options = new java.util.ArrayList<>(animal.evolutionOptions().stream()
                .filter(option -> xp >= option.xpRequired())
                .toList());
        AnimalDefinition blackDragon = AnimalDefinition.byId("blackdragon");
        if (blackDragon != null && animal.canUnlockFinal(xp)) {
            options.add(blackDragon);
        }
        return options;
    }

    public void kill() {
        this.alive = false;
        this.pendingInput = null;
    }

    public boolean canUseAbility(long currentTick) {
        return currentTick - lastAbilityTick >= ABILITY_COOLDOWN_TICKS;
    }

    public void markAbilityUsed(long currentTick) {
        this.lastAbilityTick = currentTick;
    }

    public void guardForTicks(long currentTick, long durationTicks) {
        this.guardUntilTick = Math.max(this.guardUntilTick, currentTick + durationTicks);
    }

    public long getAbilityCooldownRemainingTicks(long currentTick) {
        return Math.max(0, ABILITY_COOLDOWN_TICKS - (currentTick - lastAbilityTick));
    }

    // ------------------------------------------------------------------ getters

    public String getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public AnimalDefinition getAnimal() {
        return animal;
    }

    public String getSkinId() {
        return skinId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getAngle() {
        return angle;
    }

    public double getHealth() {
        return health;
    }

    public double getMaxHealth() {
        return animal.maxHealth();
    }

    public double getXp() {
        return xp;
    }

    /** Current water level. Exposed on the wire as {@code oceanSurvival}. */
    public double getWater() {
        return water;
    }

    /** Maximum water level. Exposed on the wire as {@code maxOceanSurvival}. */
    public double getMaxWater() {
        return maxWater;
    }

    public double getRadius() {
        return animal.radius();
    }

    public double getSpeed() {
        return animal.speed();
    }

    public boolean isAlive() {
        return alive;
    }

    @Override
    public String toString() {
        return "PlayerEntity{id='" + id + "', nickname='" + nickname
                + "', animal=" + animal.id() + ", pos=(" + x + "," + y + ")}";
    }
}
