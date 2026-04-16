package com.thun.javaagent.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe container for per-method runtime metrics.
 * <p>
 * Tracks invocation count, total/min/max/avg execution time,
 * error count, and the most recent exception type.
 *
 * @author Ali
 */
public class MethodMetrics {

    private final AtomicLong callCount = new AtomicLong(0);
    private final AtomicLong totalTimeNanos = new AtomicLong(0);
    private final AtomicLong minTimeNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxTimeNanos = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    /** Most recent exception type (e.g. "java.lang.NullPointerException"). */
    private volatile String lastExceptionType = "";

    // ------------------------------------------------------------ recording

    /**
     * Records a successful method invocation with the given duration.
     *
     * @param durationNanos elapsed time in nanoseconds
     */
    public void update(long durationNanos) {
        callCount.incrementAndGet();
        totalTimeNanos.addAndGet(durationNanos);

        // Update min (CAS loop)
        long currentMin;
        do {
            currentMin = minTimeNanos.get();
            if (durationNanos >= currentMin) break;
        } while (!minTimeNanos.compareAndSet(currentMin, durationNanos));

        // Update max (CAS loop)
        long currentMax;
        do {
            currentMax = maxTimeNanos.get();
            if (durationNanos <= currentMax) break;
        } while (!maxTimeNanos.compareAndSet(currentMax, durationNanos));
    }

    /**
     * Records an exception thrown by this method.
     *
     * @param exceptionType fully-qualified exception class name
     */
    public void recordError(String exceptionType) {
        errorCount.incrementAndGet();
        this.lastExceptionType = exceptionType;
    }

    // ------------------------------------------------------------ accessors

    public long getCallCount()        { return callCount.get(); }
    public long getTotalTimeNanos()   { return totalTimeNanos.get(); }
    public long getErrorCount()       { return errorCount.get(); }
    public String getLastExceptionType() { return lastExceptionType; }

    /**
     * @return average execution time in nanoseconds, or 0 if never called
     */
    public long getAverageTimeNanos() {
        long count = callCount.get();
        return (count == 0) ? 0 : totalTimeNanos.get() / count;
    }

    public long getMinTimeNanos() {
        long min = minTimeNanos.get();
        return (min == Long.MAX_VALUE) ? 0 : min;
    }

    public long getMaxTimeNanos() {
        return maxTimeNanos.get();
    }

    // Convenience millisecond accessors
    public double getTotalTimeMs()   { return getTotalTimeNanos()   / 1_000_000.0; }
    public double getAverageTimeMs() { return getAverageTimeNanos() / 1_000_000.0; }
    public double getMinTimeMs()     { return getMinTimeNanos()     / 1_000_000.0; }
    public double getMaxTimeMs()     { return getMaxTimeNanos()     / 1_000_000.0; }

    /**
     * @return error rate as a fraction (0.0 – 1.0), or 0 if never called
     */
    public double getErrorRate() {
        long count = callCount.get();
        return (count == 0) ? 0.0 : (double) errorCount.get() / count;
    }

    // ------------------------------------------------------------ state restore (for persistence)

    /**
     * Restores metrics from persisted values. Used by {@link MetricsRegistry} on startup.
     */
    public void restore(long calls, long totalNanos, long minNanos, long maxNanos, long errors, String lastEx) {
        callCount.set(calls);
        totalTimeNanos.set(totalNanos);
        if (minNanos > 0) minTimeNanos.set(minNanos);
        maxTimeNanos.set(maxNanos);
        errorCount.set(errors);
        this.lastExceptionType = lastEx != null ? lastEx : "";
    }

    @Override
    public String toString() {
        return String.format("MethodMetrics{calls=%d, totalMs=%.2f, avgMs=%.2f, minMs=%.2f, maxMs=%.2f, errors=%d, errRate=%.1f%%}",
                getCallCount(), getTotalTimeMs(), getAverageTimeMs(), getMinTimeMs(), getMaxTimeMs(),
                getErrorCount(), getErrorRate() * 100);
    }
}
