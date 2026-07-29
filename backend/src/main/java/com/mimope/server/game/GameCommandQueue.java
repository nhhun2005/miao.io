package com.mimope.server.game;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Thread-safe ingress queue drained exactly once at the start of every tick. */
final class GameCommandQueue {
    private final Queue<GameCommand> commands = new ConcurrentLinkedQueue<>();

    void submit(GameCommand command) {
        commands.add(command);
    }

    void drain(GameWorld world) {
        GameCommand command;
        while ((command = commands.poll()) != null) {
            command.execute(world);
        }
    }
}
