package com.thun.javaagent.export;

import com.thun.javaagent.agent.AgentConfig;
import com.thun.javaagent.metrics.MetricsRegistry;

/**
 * Background daemon that periodically persists metrics to a JSON file
 * and restores them on agent startup.
 * <p>
 * Persistence ensures that metrics survive short-lived JVM restarts and
 * can be used for offline analysis.
 *
 * @author Ali
 */
public final class MetricsPersistence {

    private volatile Thread worker;
    private volatile boolean running;

    /**
     * Attempts to load previously persisted metrics from disk.
     */
    public void loadExisting() {
        String path = AgentConfig.getInstance().getMetricsExportPath();
        MetricsRegistry.getInstance().loadFromFile(path);
    }

    /**
     * Starts a daemon thread that dumps metrics to disk at the configured interval.
     */
    public void startPeriodicDump() {
        if (running) return;
        running = true;

        int intervalSec = AgentConfig.getInstance().getPersistIntervalSec();
        String path = AgentConfig.getInstance().getMetricsExportPath();

        worker = new Thread(() -> {
            System.out.println("[agent-persist] periodic dump every " + intervalSec + "s to " + path);
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(intervalSec * 1000L);
                    MetricsRegistry.getInstance().dumpToFile(path);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[agent-persist] dump error: " + e.getMessage());
                }
            }
        }, "agent-persist");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Stops the persistence daemon and performs a final dump.
     */
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
        // Final dump
        String path = AgentConfig.getInstance().getMetricsExportPath();
        MetricsRegistry.getInstance().dumpToFile(path);
    }
}
