package com.mimope.server.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimope.server.websocket.MessageEncoder;
import com.mimope.server.websocket.SessionRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GameRoomConcurrencyTest {
    @Test
    void concurrentJoinsCannotExceedCapacity() throws Exception {
        GameRoom room = new GameRoom(new SessionRegistry(), new MessageEncoder(new ObjectMapper()),
                new SnapshotMetrics(), 5000, 5000, 1, 0, 20);
        room.init();
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<PlayerEntity> first = new AtomicReference<>();
            AtomicReference<PlayerEntity> second = new AtomicReference<>();
            Thread a = new Thread(() -> join(room, start, first, "a"));
            Thread b = new Thread(() -> join(room, start, second, "b"));
            a.start();
            b.start();
            start.countDown();
            a.join();
            b.join();
            assertEquals(1, room.getWorld().getPlayerCount());
            assertNotEquals(first.get() == null, second.get() == null);
        } finally {
            room.shutdown();
        }
    }

    private void join(GameRoom room, CountDownLatch start,
                      AtomicReference<PlayerEntity> result, String id) {
        try {
            start.await();
            result.set(room.addPlayer(id, id));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
