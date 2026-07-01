package com.mimope.server.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-rate game loop that drives the {@link GameWorld} simulation.
 * <p>
 * Runs on a dedicated daemon thread at a configurable tick rate (default 20 Hz).
 * Tracks tick duration metrics and supports graceful shutdown.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #start()} — starts the loop thread</li>
 *   <li>Each iteration: advance the world, invoke the snapshot callback</li>
 *   <li>{@link #stop()} — signals the loop to exit, joins the thread</li>
 * </ol>
 */
public class GameLoop {

    private static final Logger log = LoggerFactory.getLogger(GameLoop.class);

    private final GameWorld world;
    private final int tickRate;
    private final long tickIntervalNanos;
    private final Runnable onSnapshot;

    private Thread loopThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Tick duration metrics (nanoseconds)
    private final AtomicLong lastTickDurationNanos = new AtomicLong(0);
    private final AtomicLong maxTickDurationNanos = new AtomicLong(0);
    private final AtomicLong totalTickDurationNanos = new AtomicLong(0);
    private final AtomicLong tickCount = new AtomicLong(0);

    /**
     * @param world      the game world to advance each tick
     * @param tickRate   ticks per second (e.g. 20)
     * @param onSnapshot callback invoked after each tick (used to broadcast snapshots)
     */
    public GameLoop(GameWorld world, int tickRate, Runnable onSnapshot) {
        this.world = world;
        this.tickRate = tickRate;
        this.tickIntervalNanos = 1_000_000_000L / tickRate;
        this.onSnapshot = onSnapshot;
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Start the game loop on a dedicated daemon thread.
     *
     * @throws IllegalStateException if already running
     */
    public void start() {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("GameLoop is already running");
        }

        loopThread = new Thread(this::run, "game-loop");
        loopThread.setDaemon(true);
        loopThread.start();

        log.info("GameLoop started at {} Hz (interval={}ms)", tickRate, tickIntervalNanos / 1_000_000);
    }

    /**
     * Signal the loop to stop and wait for the thread to finish.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return; // already stopped
        }

        log.info("GameLoop stopping...");
        if (loopThread != null) {
            loopThread.interrupt();
            try {
                loopThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for game loop thread to finish");
            }
        }
        log.info("GameLoop stopped. Total ticks: {}, avg tick duration: {}µs",
                tickCount.get(),
                tickCount.get() > 0 ? (totalTickDurationNanos.get() / tickCount.get() / 1000) : 0);
    }

    public boolean isRunning() {
        return running.get();
    }

    // ------------------------------------------------------------------ main loop

    private void run() {
        log.info("Game loop thread started");
        double deltaTime = 1.0 / tickRate;

        while (running.get()) {
            long tickStart = System.nanoTime();

            try {
                // 1. Advance the simulation
                world.tick(deltaTime);

                // 2. Broadcast snapshots
                if (onSnapshot != null) {
                    onSnapshot.run();
                }
            } catch (Exception e) {
                log.error("Error in game loop tick {}: {}", world.getTick(), e.getMessage(), e);
            }

            // 3. Record metrics
            long tickDuration = System.nanoTime() - tickStart;
            recordTickMetrics(tickDuration);

            // 4. Sleep to maintain fixed tick rate
            long sleepNanos = tickIntervalNanos - tickDuration;
            if (sleepNanos > 0) {
                try {
                    long sleepMillis = sleepNanos / 1_000_000;
                    int sleepNanosRemainder = (int) (sleepNanos % 1_000_000);
                    Thread.sleep(sleepMillis, sleepNanosRemainder);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else if (sleepNanos < -tickIntervalNanos) {
                // Tick took more than 2x the budget — log a warning
                log.warn("Game loop tick {} overran by {}ms",
                        world.getTick(), (-sleepNanos) / 1_000_000);
            }
        }

        log.info("Game loop thread exiting");
    }

    // ------------------------------------------------------------------ metrics

    private void recordTickMetrics(long durationNanos) {
        lastTickDurationNanos.set(durationNanos);
        totalTickDurationNanos.addAndGet(durationNanos);
        tickCount.incrementAndGet();

        // Update max using CAS loop
        long currentMax;
        do {
            currentMax = maxTickDurationNanos.get();
        } while (durationNanos > currentMax && !maxTickDurationNanos.compareAndSet(currentMax, durationNanos));
    }

    /** Last tick duration in microseconds. */
    public long getLastTickDurationMicros() {
        return lastTickDurationNanos.get() / 1000;
    }

    /** Maximum tick duration in microseconds. */
    public long getMaxTickDurationMicros() {
        return maxTickDurationNanos.get() / 1000;
    }

    /** Average tick duration in microseconds. */
    public long getAverageTickDurationMicros() {
        long count = tickCount.get();
        return count > 0 ? (totalTickDurationNanos.get() / count / 1000) : 0;
    }

    /** Total number of ticks processed. */
    public long getTickCount() {
        return tickCount.get();
    }

    /** Configured tick rate (Hz). */
    public int getTickRate() {
        return tickRate;
    }
}
