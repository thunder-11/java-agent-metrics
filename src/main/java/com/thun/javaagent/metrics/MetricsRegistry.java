package com.thun.javaagent.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton registry that stores runtime metrics for every instrumented method.
 * Thread-safe — designed for concurrent writes from instrumented bytecode and
 * concurrent reads from the Swing dashboard.
 *
 * @author Ali
 */
public final class MetricsRegistry {

    private static final MetricsRegistry INSTANCE = new MetricsRegistry();

    private final ConcurrentHashMap<String, MethodMetrics> metricsMap = new ConcurrentHashMap<>();

    private MetricsRegistry() {
        // singleton
    }

    /**
     * @return the single global registry instance
     */
    public static MetricsRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Records a method invocation. Called from injected bytecode.
     *
     * @param methodKey  identifier in the form "ClassName#methodName"
     * @param durationNanos elapsed nanoseconds for this invocation
     */
    public void updateMetric(String methodKey, long durationNanos) {
        metricsMap.computeIfAbsent(methodKey, k -> new MethodMetrics()).update(durationNanos);
    }

    /**
     * Returns a <b>snapshot copy</b> of the current metrics map.
     * Safe to iterate on the UI thread without risking ConcurrentModificationException.
     */
    public Map<String, MethodMetrics> getMetricsSnapshot() {
        return new ConcurrentHashMap<>(metricsMap);
    }

    /**
     * @return the live map (use only when snapshot semantics aren't needed)
     */
    public ConcurrentHashMap<String, MethodMetrics> getLiveMetrics() {
        return metricsMap;
    }

    /**
     * Clears all collected metrics.
     */
    public void clear() {
        metricsMap.clear();
    }
}
