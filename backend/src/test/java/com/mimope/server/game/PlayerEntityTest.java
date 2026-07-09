package com.mimope.server.game;

import com.mimope.server.game.data.AnimalDefinition;
import com.mimope.server.protocol.inbound.InputMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerEntityTest {

    private AnimalDefinition mouse;
    private PlayerEntity player;

    @BeforeEach
    void setUp() {
        mouse = AnimalDefinition.starter(); // mouse: speed=200, radius=22, maxHealth=2
        player = new PlayerEntity("p1", "Alice", mouse, 500, 500);
    }

    // ------------------------------------------------------------------ construction

    @Test
    void constructorSetsFields() {
        assertEquals("p1", player.getId());
        assertEquals("Alice", player.getNickname());
        assertEquals(mouse, player.getAnimal());
        assertEquals(500, player.getX());
        assertEquals(500, player.getY());
        assertEquals(0, player.getAngle());
        assertEquals(2, player.getHealth());
        assertEquals(2, player.getMaxHealth());
        assertEquals(0, player.getXp());
        assertEquals(22, player.getRadius());
        assertEquals(200, player.getSpeed());
        assertTrue(player.isAlive());
    }

    @Test
    void constructorSetsFullHealthFromAnimalTier() {
        assertEquals(13, new PlayerEntity("p2", "Shark", AnimalDefinition.byId("shark"), 0, 0).getHealth());
        assertEquals(16, new PlayerEntity("p3", "Dragon", AnimalDefinition.byId("dragon"), 0, 0).getHealth());
        assertEquals(20, new PlayerEntity("p4", "BlackDragon", AnimalDefinition.byId("blackdragon"), 0, 0).getHealth());
    }

    // ------------------------------------------------------------------ input queue

    @Test
    void queueAndConsumeInput() {
        assertNull(player.consumeInput(), "No input initially");

        InputMessage input = new InputMessage(1, 0.0, 1.0, false, false, 0L);
        player.queueInput(input);

        InputMessage consumed = player.consumeInput();
        assertNotNull(consumed);
        assertEquals(1, consumed.seq());

        assertNull(player.consumeInput(), "Input consumed, should be null");
    }

    @Test
    void latestInputWins() {
        player.queueInput(new InputMessage(1, 0.0, 1.0, false, false, 0L));
        player.queueInput(new InputMessage(2, 1.0, 0.5, false, false, 0L));

        InputMessage consumed = player.consumeInput();
        assertNotNull(consumed);
        assertEquals(2, consumed.seq(), "Only the latest input should be kept");
    }

    // ------------------------------------------------------------------ movement

    @Test
    void applyMovementMovesRight() {
        // angle=0 => move right, intensity=1.0, deltaTime=1.0s, speed=200
        InputMessage input = new InputMessage(1, 0.0, 1.0, false, false, 0L);
        player.applyMovement(input, 1.0, 5000, 5000);

        assertEquals(700, player.getX(), 0.01, "Should move 200 units right");
        assertEquals(500, player.getY(), 0.01, "Y should not change");
        assertEquals(0.0, player.getAngle(), 0.01);
    }

    @Test
    void applyMovementMovesDown() {
        // angle=π/2 => move down
        double angle = Math.PI / 2;
        InputMessage input = new InputMessage(1, angle, 1.0, false, false, 0L);
        player.applyMovement(input, 1.0, 5000, 5000);

        assertEquals(500, player.getX(), 0.01);
        assertEquals(700, player.getY(), 0.01, "Should move 200 units down");
    }

    @Test
    void applyMovementWithBoost() {
        // Boost increases speed by 50%: 200 * 1.5 = 300
        InputMessage input = new InputMessage(1, 0.0, 1.0, true, false, 0L);
        player.applyMovement(input, 1.0, 5000, 5000);

        assertEquals(800, player.getX(), 0.01, "Boost: 500 + 300 = 800");
    }

    @Test
    void applyMovementWithIntensity() {
        // Half intensity: speed * 0.5 = 100
        InputMessage input = new InputMessage(1, 0.0, 0.5, false, false, 0L);
        player.applyMovement(input, 1.0, 5000, 5000);

        assertEquals(600, player.getX(), 0.01, "Half intensity: 500 + 100 = 600");
    }

    @Test
    void applyMovementClampsToWorldBounds() {
        // Move far right beyond world width (5000)
        InputMessage input = new InputMessage(1, 0.0, 1.0, false, false, 0L);
        // Place player near right edge
        PlayerEntity nearEdge = new PlayerEntity("p2", "Bob", mouse, 4990, 500);
        nearEdge.applyMovement(input, 1.0, 5000, 5000);

        // Should clamp: max x = worldWidth - radius = 5000 - 22 = 4978
        assertEquals(5000 - mouse.radius(), nearEdge.getX(), 0.01);
    }

    @Test
    void applyMovementClampsToLeftBound() {
        // Move left (angle=π)
        InputMessage input = new InputMessage(1, Math.PI, 1.0, false, false, 0L);
        PlayerEntity nearLeft = new PlayerEntity("p3", "Charlie", mouse, 30, 500);
        nearLeft.applyMovement(input, 1.0, 5000, 5000);

        // Should clamp: min x = radius = 22
        assertEquals(mouse.radius(), nearLeft.getX(), 0.01);
    }

    @Test
    void applyMovementWithDeltaTime() {
        // 0.05s tick at speed 200 => 10 units
        InputMessage input = new InputMessage(1, 0.0, 1.0, false, false, 0L);
        player.applyMovement(input, 0.05, 5000, 5000);

        assertEquals(510, player.getX(), 0.01, "0.05s * 200 = 10 units");
    }

    // ------------------------------------------------------------------ state changes

    @Test
    void addXp() {
        assertEquals(0, player.getXp());
        player.addXp(50);
        assertEquals(50, player.getXp());
        player.addXp(25);
        assertEquals(75, player.getXp());
    }

    @Test
    void setAnimalUpdatesStats() {
        AnimalDefinition lion = AnimalDefinition.byId("lion");
        assertNotNull(lion);

        player.setAnimal(lion);
        assertEquals(lion, player.getAnimal());
        assertEquals(lion.maxHealth(), player.getHealth(), "Health should reset to new animal's max");
        assertEquals(lion.radius(), player.getRadius());
        assertEquals(lion.speed(), player.getSpeed());
    }

    @Test
    void biteDamageSubtractsExactlyOneHp() {
        player.damageByBite();

        assertEquals(1, player.getHealth());
        assertFalse(player.isDeadByHealth());

        player.damageByBite();

        assertEquals(0, player.getHealth());
        assertTrue(player.isDeadByHealth());
    }

    @Test
    void kill() {
        assertTrue(player.isAlive());
        player.kill();
        assertFalse(player.isAlive());
    }

    @Test
    void setNickname() {
        player.setNickname("NewName");
        assertEquals("NewName", player.getNickname());
    }

    @Test
    void toStringContainsInfo() {
        String str = player.toString();
        assertTrue(str.contains("p1"));
        assertTrue(str.contains("Alice"));
        assertTrue(str.contains("mouse"));
    }
}
