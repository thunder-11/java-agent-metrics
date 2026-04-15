package com.ali.javaagent.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe container for per-method runtime metrics.
 *
 * @author Ali
 */
public class MethodMetrics {

    private final AtomicLong callCount = new AtomicLong(0);
    private final AtomicLong totalTimeNanos = new AtomicLong(0);

    /**
     * Records a single method invocation with the given duration.
     *
     * @param durationNanos elapsed time in nanoseconds
     */
    public void update(long durationNanos) {
        callCount.incrementAndGet();
        totalTimeNanos.addAndGet(durationNanos);
    }

    public long getCallCount() {
        return callCount.get();
    }

    public long getTotalTimeNanos() {
        return totalTimeNanos.get();
    }

    /**
     * @return average execution time in nanoseconds, or 0 if never called
     */
    public long getAverageTimeNanos() {
        long count = callCount.get();
        return (count == 0) ? 0 : totalTimeNanos.get() / count;
    }

    @Override
    public String toString() {
        return String.format("MethodMetrics{calls=%d, totalNs=%d, avgNs=%d}",
                getCallCount(), getTotalTimeNanos(), getAverageTimeNanos());
    }
}
