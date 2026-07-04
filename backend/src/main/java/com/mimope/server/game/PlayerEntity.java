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

        // Boost: 50% speed increase
        if (input.boost()) {
            speed *= 1.5;
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

    // ------------------------------------------------------------------ state changes

    public void addXp(double amount) {
        this.xp += amount;
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

    public void setAnimal(AnimalDefinition animal) {
        setAnimal(animal, animal.id());
    }

    public void setAnimal(AnimalDefinition animal, String skinId) {
        this.animal = animal;
        this.skinId = skinId;
        this.health = animal.maxHealth();
        this.evolutionOptionsSent = false;
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
