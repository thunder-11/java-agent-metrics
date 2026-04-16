package com.thun.javaagent.agent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Centralised, thread-safe runtime configuration for the Java Agent.
 * <p>
 * Parsed once from the agent argument string at startup and can be
 * reconfigured dynamically at runtime via JMX or the HTTP config endpoint.
 * <p>
 * Agent argument format (semicolon-separated key=value pairs):
 * <pre>
 *   target=com.thun.javaagent.Test;threshold=100;port=8686;export=./metrics.json
 * </pre>
 *
 * @author Ali
 */
public final class AgentConfig {

    // ------------------------------------------------------------ singleton
    private static final AgentConfig INSTANCE = new AgentConfig();

    public static AgentConfig getInstance() {
        return INSTANCE;
    }

    private AgentConfig() { /* singleton */ }

    // ------------------------------------------------------------ fields

    /** Master kill-switch for instrumentation. */
    private volatile boolean enabled = true;

    /** Target class/package passed as the first positional agent argument. */
    private volatile String targetPackage = "";

    /** Methods slower than this (ms) are flagged as "slow". */
    private volatile long slowThresholdMs = 100;

    /** Port for the built-in HTTP metrics server. */
    private volatile int httpPort = 8686;

    /** Filesystem path for periodic metrics dump (JSON). */
    private volatile String metricsExportPath = "./agent-metrics.json";

    /** Persistence dump interval in seconds. */
    private volatile int persistIntervalSec = 30;

    /** Package prefixes to include (empty = include everything non-excluded). */
    private final CopyOnWriteArrayList<String> packageFilters = new CopyOnWriteArrayList<>();

    /** Specific class names to exclude. */
    private final CopyOnWriteArrayList<String> classExclusions = new CopyOnWriteArrayList<>();

    /** Specific method names to exclude. */
    private final CopyOnWriteArrayList<String> methodExclusions = new CopyOnWriteArrayList<>();

    // ------------------------------------------------------------ parsing

    /**
     * Parses the raw agent argument string into this config instance.
     * <p>
     * Supports two formats:
     * <ul>
     *   <li>Legacy: a single class name (e.g. {@code com.thun.javaagent.Test})</li>
     *   <li>Extended: semicolon-separated key=value pairs</li>
     * </ul>
     */
    public void parse(String agentArgs) {
        if (agentArgs == null || agentArgs.trim().isEmpty()) {
            return;
        }

        // Legacy format: single class name with no '=' or ';'
        if (!agentArgs.contains("=") && !agentArgs.contains(";")) {
            this.targetPackage = agentArgs.trim();
            this.packageFilters.addIfAbsent(this.targetPackage);
            return;
        }

        String[] pairs = agentArgs.split(";");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim().toLowerCase();
            String val = kv[1].trim();
            switch (key) {
                case "target":       
                    this.targetPackage = val; 
                    this.packageFilters.addIfAbsent(val);
                    break;
                case "threshold":    this.slowThresholdMs = Long.parseLong(val); break;
                case "port":         this.httpPort = Integer.parseInt(val); break;
                case "export":       this.metricsExportPath = val; break;
                case "persist":      this.persistIntervalSec = Integer.parseInt(val); break;
                case "enabled":      this.enabled = Boolean.parseBoolean(val); break;
                case "packages":     this.packageFilters.addAll(Arrays.asList(val.split(","))); break;
                case "exclude-classes":  this.classExclusions.addAll(Arrays.asList(val.split(","))); break;
                case "exclude-methods":  this.methodExclusions.addAll(Arrays.asList(val.split(","))); break;
                default:
                    System.out.println("[agent-config] unknown key: " + key);
            }
        }
    }

    // ------------------------------------------------------------ getters / setters

    public boolean isEnabled()           { return enabled; }
    public void setEnabled(boolean b)    { this.enabled = b; }

    public String getTargetPackage()     { return targetPackage; }

    public long getSlowThresholdMs()     { return slowThresholdMs; }
    public void setSlowThresholdMs(long ms) { this.slowThresholdMs = ms; }

    public int getHttpPort()             { return httpPort; }
    public String getMetricsExportPath() { return metricsExportPath; }
    public int getPersistIntervalSec()   { return persistIntervalSec; }

    public List<String> getPackageFilters()  { return Collections.unmodifiableList(packageFilters); }
    public List<String> getClassExclusions() { return Collections.unmodifiableList(classExclusions); }
    public List<String> getMethodExclusions(){ return Collections.unmodifiableList(methodExclusions); }

    public void addPackageFilter(String pkg)    { packageFilters.addIfAbsent(pkg); }
    public void removePackageFilter(String pkg) { packageFilters.remove(pkg); }
    public void addClassExclusion(String cls)   { classExclusions.addIfAbsent(cls); }
    public void addMethodExclusion(String m)    { methodExclusions.addIfAbsent(m); }

    // ------------------------------------------------------------ toString

    @Override
    public String toString() {
        return "AgentConfig{" +
                "enabled=" + enabled +
                ", target='" + targetPackage + '\'' +
                ", slowThreshold=" + slowThresholdMs + "ms" +
                ", httpPort=" + httpPort +
                ", exportPath='" + metricsExportPath + '\'' +
                ", persistInterval=" + persistIntervalSec + "s" +
                ", packageFilters=" + packageFilters +
                '}';
    }
}
