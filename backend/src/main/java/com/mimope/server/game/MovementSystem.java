package com.mimope.server.game;

import com.mimope.server.protocol.inbound.InputMessage;

import java.util.Collection;
import java.util.function.ToDoubleFunction;

/** Resolves retained latest-wins steering and dash movement for alive players. */
final class MovementSystem {
    private static final double DASH_SPEED_MULTIPLIER = 3.0;

    void update(Collection<PlayerEntity> players, double deltaTime, double width, double height,
                long tick, ToDoubleFunction<PlayerEntity> biomeMultiplier,
                WorldEventBuffer events) {
        for (PlayerEntity player : players) {
            if (!player.isAlive()) continue;
            InputMessage input = player.resolveMovementInput();
            if (input == null) continue;
            if (input.dash() && player.canDash(tick)) {
                player.markDashUsed(tick);
                events.dashes.add(new DashEvent(player.getId(), player.getX(), player.getY(), input.angle()));
            }
            double dashMultiplier = 1.0;
            if (player.isDashing()) {
                dashMultiplier = DASH_SPEED_MULTIPLIER;
                player.advanceDash();
            }
            player.applyMovement(input, deltaTime, width, height,
                    dashMultiplier * biomeMultiplier.applyAsDouble(player));
        }
    }
}
