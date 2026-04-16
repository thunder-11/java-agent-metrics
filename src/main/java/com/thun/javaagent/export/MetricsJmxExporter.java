package com.thun.javaagent.export;

import com.thun.javaagent.agent.AgentConfig;
import com.thun.javaagent.metrics.MetricsRegistry;

import javax.management.*;
import java.lang.management.ManagementFactory;

/**
 * Exposes agent metrics and controls via JMX (Java Management Extensions).
 * <p>
 * Registered under {@code com.thun.javaagent:type=Metrics}.
 * Viewable with JConsole, VisualVM, or any JMX client.
 *
 * @author Ali
 */
public final class MetricsJmxExporter implements MetricsJmxExporterMBean {

    private static final String OBJECT_NAME = "com.thun.javaagent:type=Metrics";

    /**
     * Registers this MBean with the platform MBean server.
     */
    public void register() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName(OBJECT_NAME);
            if (!mbs.isRegistered(name)) {
                mbs.registerMBean(this, name);
                System.out.println("[agent-jmx] MBean registered: " + OBJECT_NAME);
            }
        } catch (Exception e) {
            System.err.println("[agent-jmx] registration failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ MBean attributes

    @Override
    public int getTotalMethodsTracked() {
        return MetricsRegistry.getInstance().getMethodCount();
    }

    @Override
    public long getTotalInvocations() {
        return MetricsRegistry.getInstance().getTotalInvocations();
    }

    @Override
    public long getTotalErrors() {
        return MetricsRegistry.getInstance().getTotalErrors();
    }

    @Override
    public long getUptimeMs() {
        return MetricsRegistry.getInstance().getUptimeMillis();
    }

    @Override
    public boolean isEnabled() {
        return AgentConfig.getInstance().isEnabled();
    }

    @Override
    public long getSlowThresholdMs() {
        return AgentConfig.getInstance().getSlowThresholdMs();
    }

    // ------------------------------------------------------------ MBean operations

    @Override
    public void setEnabled(boolean enabled) {
        AgentConfig.getInstance().setEnabled(enabled);
        System.out.println("[agent-jmx] instrumentation " + (enabled ? "enabled" : "disabled"));
    }

    @Override
    public void setSlowThreshold(long thresholdMs) {
        AgentConfig.getInstance().setSlowThresholdMs(thresholdMs);
        System.out.println("[agent-jmx] slow threshold set to " + thresholdMs + "ms");
    }

    @Override
    public void resetMetrics() {
        MetricsRegistry.getInstance().clear();
        System.out.println("[agent-jmx] metrics cleared");
    }
}
