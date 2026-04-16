package com.thun.javaagent.export;

/**
 * MBean interface for {@link MetricsJmxExporter}.
 * <p>
 * Defines the attributes and operations exposed via JMX.
 *
 * @author Ali
 */
public interface MetricsJmxExporterMBean {

    // Attributes (read-only)
    int getTotalMethodsTracked();
    long getTotalInvocations();
    long getTotalErrors();
    long getUptimeMs();
    boolean isEnabled();
    long getSlowThresholdMs();

    // Operations
    void setEnabled(boolean enabled);
    void setSlowThreshold(long thresholdMs);
    void resetMetrics();
}
