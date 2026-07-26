package com.mimope.server.game;

import com.mimope.server.game.data.AnimalDefinition;
import com.mimope.server.game.data.Biome;
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

    // Position & motion
    private double x;
    private double y;
    private double angle;

    // Stats
    private double health;
    private double xp;

    /**
     * Drinking-water reserve carried by every creature. Dashing drains it,
     * standing in a water source or eating food refills it, and running low
     * disables dashing while running dry slowly drains health (dehydration).
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
     * Water drained per second by an ocean creature stranded out of water
     * (25% of the maximum per second). A beached sea animal runs dry in four
     * seconds and then starts taking dehydration damage, so leaving the water
     * is a short, deliberate gamble rather than a viable way to travel.
     */
    private static final double WATER_DRAIN_PER_SECOND_BEACHED = MAX_WATER * 0.25;

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

    /**
     * The most recently applied steering input, retained after it is consumed.
     * <p>
     * Client input arrives at ~20 Hz on its own timer, unsynchronised with the
     * server tick, so some ticks receive no packet while others receive two
     * (the extra one being dropped by the latest-wins queue). Moving only on
     * ticks that happened to carry a packet made the creature stall and surge,
     * i.e. a speed that visibly fluctuates. Holding the last steering input and
     * re-applying it keeps the distance travelled per second constant.
     */
    private InputMessage lastMovementInput;

    // Lifecycle
    private boolean alive = true;

    // Evolution
    private boolean evolutionOptionsSent = false;

    /**
     * Dash: a short speed burst triggered by the player. Costs a flat slice of
     * the water bar per activation and has a 1.5-second cooldown (30 ticks at
     * the default 20 Hz tick rate). Dashing is blocked while the water bar sits
     * below {@link #DASH_MIN_WATER}.
     * <p>
     * Holding a dash control keeps the request raised on every input frame, so
     * the cooldown doubles as the auto-repeat interval.
     */
    private static final long DASH_COOLDOWN_TICKS = 30;
    private static final double DASH_WATER_COST = MAX_WATER * 0.05;
    private static final double DASH_MIN_WATER = MAX_WATER * 0.10;
    private long lastDashTick = -DASH_COOLDOWN_TICKS;

    /**
     * How many ticks the dash burst lasts (~0.15s at the default 20 Hz). A
     * single-tick burst moved the creature by only a few pixels, so the dash
     * has to stay active for a short window to read as a forward push — but the
     * window stays short, since the burst length is what sets the dash
     * distance.
     */
    private static final int DASH_DURATION_TICKS = 3;
    private int dashTicksRemaining = 0;

    public PlayerEntity(String id, String nickname, AnimalDefinition animal, double x, double y) {
        this.id = id;
        this.nickname = nickname;
        this.animal = animal;
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

    /**
     * Resolve the steering input to simulate on this tick: the freshly queued
     * input when one arrived, otherwise the last input that was applied.
     * <p>
     * Returns {@code null} only before the player has ever sent an input.
     *
     * @see #lastMovementInput
     */
    public InputMessage resolveMovementInput() {
        InputMessage fresh = consumeInput();
        if (fresh == null) {
            return lastMovementInput;
        }
        // Retain steering only: dash is single-fire, so the held-over copy must
        // not re-trigger it on ticks that arrive without a fresh packet.
        this.lastMovementInput = new InputMessage(
                fresh.seq(), fresh.angle(), fresh.intensity(), false, fresh.timestamp());
        return fresh;
    }

    // ------------------------------------------------------------------ movement

    /**
     * Apply one tick of movement based on the given input.
     *
     * @param input     the player input (angle, intensity, dash)
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
        this.animal = animal;
        this.health = animal.maxHealth();
        this.evolutionOptionsSent = false;
        resetWater();
    }

    /**
     * Whether this creature is an ocean animal currently out of water. Only
     * meaningful when the caller has established it is not in a water source.
     */
    private boolean isBeached() {
        return animal.biome() == Biome.OCEAN;
    }

    /**
     * Update the drinking-water bar each tick.
     * <p>
     * While inside a water source (the ocean or a puddle) the bar refills at
     * {@code 50%} of the maximum per second. Otherwise the bar drains over
     * time (thirst) — {@code 2%} of the maximum per second for land and arctic
     * animals, but {@code 25%} per second for an ocean animal beached out of
     * water. When the bar reaches zero the creature dehydrates and loses
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

        // Thirst: the water bar drains over time whenever the creature is not
        // standing in a water source. Ocean creatures out of water are beached
        // and drain more than ten times faster.
        double drainPerSecond = isBeached()
                ? WATER_DRAIN_PER_SECOND_BEACHED
                : WATER_DRAIN_PER_SECOND_PASSIVE;
        this.water = Math.max(0, this.water - drainPerSecond * deltaTime);

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
        this.lastMovementInput = null;
        this.dashTicksRemaining = 0;
    }

    // ------------------------------------------------------------------ dash

    /**
     * Whether the creature can dash this tick: the cooldown must be up and the
     * water bar must sit at or above the minimum threshold.
     */
    public boolean canDash(long currentTick) {
        return currentTick - lastDashTick >= DASH_COOLDOWN_TICKS && water >= DASH_MIN_WATER;
    }

    /**
     * Register a dash: start the cooldown, pay the flat water cost and open the
     * burst window during which the creature is pushed forward.
     */
    public void markDashUsed(long currentTick) {
        this.lastDashTick = currentTick;
        this.water = Math.max(0, this.water - DASH_WATER_COST);
        this.dashTicksRemaining = DASH_DURATION_TICKS;
    }

    /** Whether the forward dash burst is still active. */
    public boolean isDashing() {
        return dashTicksRemaining > 0;
    }

    /** Consume one tick of the active dash burst. */
    public void advanceDash() {
        if (dashTicksRemaining > 0) {
            dashTicksRemaining--;
        }
    }

    public long getDashCooldownRemainingTicks(long currentTick) {
        return Math.max(0, DASH_COOLDOWN_TICKS - (currentTick - lastDashTick));
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
