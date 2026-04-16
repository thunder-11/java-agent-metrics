package com.thun.javaagent;

import com.thun.javaagent.agent.AgentConfig;
import com.thun.javaagent.export.MetricsHttpServer;
import com.thun.javaagent.export.MetricsJmxExporter;
import com.thun.javaagent.export.MetricsPersistence;
import com.thun.javaagent.ui.MetricsDashboard;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent entry point.
 * <p>
 * Bootstraps the entire APM system:
 * <ol>
 *   <li>Parses agent arguments into {@link AgentConfig}</li>
 *   <li>Loads persisted metrics from disk (if available)</li>
 *   <li>Registers the {@link AOPTransformer} for bytecode instrumentation</li>
 *   <li>Starts the HTTP metrics server</li>
 *   <li>Registers JMX MBean</li>
 *   <li>Starts persistence daemon</li>
 *   <li>Launches the {@link MetricsDashboard}</li>
 * </ol>
 *
 * @author Ali
 */
public class AgentMain {

    /**
     * Called when the agent is loaded via {@code -javaagent} at JVM startup.
     */
    public static void premain(String agentOps, Instrumentation inst) {
        System.out.println("[javaagent] premain called, args=" + agentOps);
        instrument(agentOps, inst);
    }

    /**
     * Called when the agent is attached to a running JVM (e.g. via ByteBuddy).
     */
    public static void agentmain(String agentOps, Instrumentation inst) {
        System.out.println("[javaagent] agentmain called, args=" + agentOps);
        instrument(agentOps, inst);
    }

    /**
     * Bootstraps the full APM pipeline.
     */
    private static void instrument(String agentOps, Instrumentation inst) {
        // 1. Parse configuration
        AgentConfig config = AgentConfig.getInstance();
        config.parse(agentOps);
        System.out.println("[javaagent] config: " + config);

        // 2. Load persisted metrics
        MetricsPersistence persistence = new MetricsPersistence();
        persistence.loadExisting();

        // 3. Register bytecode transformer
        String target = config.getTargetPackage();
        System.out.println("[javaagent] adding transformer for target=" + target);
        inst.addTransformer(new AOPTransformer(target));

        // 4. Start HTTP metrics server
        MetricsHttpServer httpServer = new MetricsHttpServer(config.getHttpPort());
        httpServer.start();

        // 5. Register JMX MBean
        MetricsJmxExporter jmx = new MetricsJmxExporter();
        jmx.register();

        // 6. Start persistence daemon
        persistence.startPeriodicDump();

        // 7. Launch Swing dashboard on a daemon thread
        Thread dashboardThread = new Thread(MetricsDashboard::launch, "metrics-dashboard");
        dashboardThread.setDaemon(true);
        dashboardThread.start();
        System.out.println("[javaagent] APM system fully initialized");
    }
}
