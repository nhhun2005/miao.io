package com.mimope.server.game;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GameLoopTest {

    private GameLoop loop;

    @AfterEach
    void tearDown() {
        if (loop != null && loop.isRunning()) {
            loop.stop();
        }
    }

    private GameWorld createWorld() {
        return new GameWorld(5000, 5000, 10);
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    void startAndStop() throws InterruptedException {
        GameWorld world = createWorld();
        loop = new GameLoop(world, 20, () -> {});

        assertFalse(loop.isRunning());
        loop.start();
        assertTrue(loop.isRunning());

        // Let it run a few ticks
        Thread.sleep(150);
        assertTrue(loop.getTickCount() > 0, "Should have processed some ticks");

        loop.stop();
        assertFalse(loop.isRunning());
    }

    @Test
    void startTwiceThrows() {
        GameWorld world = createWorld();
        loop = new GameLoop(world, 20, () -> {});
        loop.start();

        assertThrows(IllegalStateException.class, () -> loop.start());
    }

    @Test
    void stopWhenNotRunningIsNoop() {
        GameWorld world = createWorld();
        loop = new GameLoop(world, 20, () -> {});

        // Should not throw
        assertDoesNotThrow(() -> loop.stop());
    }

    @Test
    void stopCanBeCalledTwice() {
        GameWorld world = createWorld();
        loop = new GameLoop(world, 20, () -> {});
        loop.start();
        loop.stop();
        assertDoesNotThrow(() -> loop.stop());
    }

    // ------------------------------------------------------------------ ticking

    @Test
    void worldTicksAdvance() throws InterruptedException {
        GameWorld world = createWorld();
        loop = new GameLoop(world, 20, () -> {});
        loop.start();

        Thread.sleep(200); // ~4 ticks at 20Hz
        loop.stop();

        assertTrue(world.getTick() > 0, "World tick counter should have advanced");
    }

    @Test
    void snapshotCallbackInvoked() throws InterruptedException {
        GameWorld world = createWorld();
        AtomicInteger callbackCount = new AtomicInteger(0);
        loop = new GameLoop(world, 20, callbackCount::incrementAndGet);
        loop.start();

        Thread.sleep(200);
        loop.stop();

        assertTrue(callbackCount.get() > 0, "Snapshot callback should have been invoked");
        assertEquals(world.getTick(), callbackCount.get(),
                "Callback count should match world tick count");
    }

    @Test
    void nullSnapshotCallbackDoesNotCrash() throws InterruptedException {
        GameWorld world = createWorld();
        loop = new GameLoop(world, 20, null);
        loop.start();

        Thread.sleep(100);
        loop.stop();

        assertTrue(world.getTick() > 0, "Should still tick even with null callback");
    }

    // ------------------------------------------------------------------ metrics

    @Test
    void metricsTrackTickDurations() throws InterruptedException {
        GameWorld world = createWorld();
        loop = new GameLoop(world, 20, () -> {});
        loop.start();

        Thread.sleep(200);
        loop.stop();

        assertTrue(loop.getTickCount() > 0);
        assertTrue(loop.getLastTickDurationMicros() >= 0);
        assertTrue(loop.getMaxTickDurationMicros() >= 0);
        assertTrue(loop.getAverageTickDurationMicros() >= 0);
        assertTrue(loop.getMaxTickDurationMicros() >= loop.getAverageTickDurationMicros(),
                "Max should be >= average");
    }

    @Test
    void tickRateAccessor() {
        GameWorld world = createWorld();
        loop = new GameLoop(world, 20, () -> {});
        assertEquals(20, loop.getTickRate());
    }

    @Test
    void metricsBeforeStartAreZero() {
        GameWorld world = createWorld();
        loop = new GameLoop(world, 20, () -> {});

        assertEquals(0, loop.getTickCount());
        assertEquals(0, loop.getLastTickDurationMicros());
        assertEquals(0, loop.getMaxTickDurationMicros());
        assertEquals(0, loop.getAverageTickDurationMicros());
    }
}
