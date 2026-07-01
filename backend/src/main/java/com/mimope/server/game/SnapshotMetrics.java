package com.mimope.server.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks snapshot payload sizes to measure the effectiveness of
 * spatial-grid-based visibility filtering (Phase 11).
 * <p>
 * Every {@value #REPORT_INTERVAL} snapshots a summary is logged showing
 * average / max / min sizes for both filtered and unfiltered snapshots.
 */
@Component
public class SnapshotMetrics {

    private static final Logger log = LoggerFactory.getLogger(SnapshotMetrics.class);

    /** Log a report every N snapshots. */
    private static final int REPORT_INTERVAL = 600; // 30 seconds at 20 Hz

    private final AtomicLong snapshotCount = new AtomicLong(0);

    private long totalFilteredBytes = 0;
    private long totalUnfilteredBytes = 0;
    private long maxFilteredBytes = 0;
    private long minFilteredBytes = Long.MAX_VALUE;
    private long maxUnfilteredBytes = 0;
    private long minUnfilteredBytes = Long.MAX_VALUE;

    /**
     * Record the size of one filtered snapshot and its unfiltered equivalent.
     *
     * @param filteredBytes   serialized size after viewport filtering
     * @param unfilteredBytes serialized size without any filtering
     */
    public synchronized void record(int filteredBytes, int unfilteredBytes) {
        totalFilteredBytes += filteredBytes;
        totalUnfilteredBytes += unfilteredBytes;

        maxFilteredBytes = Math.max(maxFilteredBytes, filteredBytes);
        minFilteredBytes = Math.min(minFilteredBytes, filteredBytes);
        maxUnfilteredBytes = Math.max(maxUnfilteredBytes, unfilteredBytes);
        minUnfilteredBytes = Math.min(minUnfilteredBytes, unfilteredBytes);

        long count = snapshotCount.incrementAndGet();
        if (count % REPORT_INTERVAL == 0) {
            logReport(count);
        }
    }

    private synchronized void logReport(long count) {
        long avgFiltered = totalFilteredBytes / count;
        long avgUnfiltered = totalUnfilteredBytes / count;
        double reductionPct = avgUnfiltered > 0
                ? (1.0 - (double) avgFiltered / avgUnfiltered) * 100.0
                : 0.0;

        log.info(
            "SnapshotMetrics [{} ticks] — filtered: avg={}B max={}B min={}B | " +
            "unfiltered: avg={}B max={}B min={}B | reduction={:.1f}%",
            count,
            avgFiltered, maxFilteredBytes, minFilteredBytes,
            avgUnfiltered, maxUnfilteredBytes, minUnfilteredBytes,
            reductionPct
        );

        // Reset min trackers so they reflect the next window
        minFilteredBytes = Long.MAX_VALUE;
        minUnfilteredBytes = Long.MAX_VALUE;
    }

    /** Reset all counters (useful for tests). */
    public synchronized void reset() {
        snapshotCount.set(0);
        totalFilteredBytes = 0;
        totalUnfilteredBytes = 0;
        maxFilteredBytes = 0;
        minFilteredBytes = Long.MAX_VALUE;
        maxUnfilteredBytes = 0;
        minUnfilteredBytes = Long.MAX_VALUE;
    }

    // ------------------------------------------------------------------ getters for tests

    public long getSnapshotCount() {
        return snapshotCount.get();
    }

    public synchronized long getTotalFilteredBytes() {
        return totalFilteredBytes;
    }

    public synchronized long getTotalUnfilteredBytes() {
        return totalUnfilteredBytes;
    }
}
