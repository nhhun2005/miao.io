package com.mimope.server.game;

import com.mimope.server.game.data.AnimalDefinition;
import com.mimope.server.protocol.inbound.InputMessage;
import com.mimope.server.websocket.InboundMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InputSecurityTest {
    @Test
    void clampsFiniteIntensityAndNormalizesAngle() {
        InputMessage input = InputMessage.from(new InboundMessage("input", Map.of(
                "seq", 1, "angle", Math.PI * 5, "intensity", 1000)));
        assertNotNull(input);
        assertEquals(1.0, input.intensity());
        assertTrue(input.angle() >= -Math.PI && input.angle() <= Math.PI);
    }

    @Test
    void rejectsNonFiniteNumbers() {
        assertNull(parse(Double.NaN, 1));
        assertNull(parse(Double.POSITIVE_INFINITY, 1));
        assertNull(parse(0, Double.NEGATIVE_INFINITY));
    }

    @Test
    void negativeIntensityCannotMovePlayer() {
        InputMessage input = InputMessage.from(new InboundMessage("input", Map.of(
                "seq", 1, "angle", 0, "intensity", -10)));
        PlayerEntity player = player();
        player.applyMovement(input, 1, 5000, 5000);
        assertEquals(1000, player.getX());
    }

    @Test
    void staleAndDuplicateSequencesAreIgnored() {
        PlayerEntity player = player();
        assertTrue(player.queueInput(new InputMessage(2, 0, 1, false, 0)));
        assertFalse(player.queueInput(new InputMessage(2, 1, 1, false, 0)));
        assertFalse(player.queueInput(new InputMessage(1, 1, 1, false, 0)));
        assertTrue(player.queueInput(new InputMessage(3, 1, 1, false, 0)));
        assertEquals(3, player.consumeInput().seq());
    }

    @Test
    void invalidInputCannotPoisonFollowingTicks() {
        PlayerEntity player = player();
        player.applyMovement(new InputMessage(1, Double.NaN, Double.POSITIVE_INFINITY, false, 0),
                .05, 5000, 5000);
        player.applyMovement(new InputMessage(2, 0, 1, false, 0), .05, 5000, 5000);
        assertTrue(Double.isFinite(player.getX()));
        assertTrue(Double.isFinite(player.getY()));
        assertTrue(Double.isFinite(player.getAngle()));
    }

    private InputMessage parse(double angle, double intensity) {
        return InputMessage.from(new InboundMessage("input", Map.of(
                "seq", 1, "angle", angle, "intensity", intensity)));
    }

    private PlayerEntity player() {
        return new PlayerEntity("p", "Player", AnimalDefinition.starter(), 1000, 1000);
    }
}
