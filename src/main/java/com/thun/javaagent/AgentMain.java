package com.thun.javaagent;

import com.thun.javaagent.ui.MetricsDashboard;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent entry point.
 * <p>
 * Registers the {@link AOPTransformer} for bytecode instrumentation and
 * launches the {@link MetricsDashboard} on a daemon thread so that metrics
 * can be observed in real time.
 *
 * @author Ali
 */
public class AgentMain {

    /**
     * Called when the agent is loaded via {@code -javaagent} at JVM startup.
     */
    public static void premain(String agentOps, Instrumentation inst) {
        System.out.println("[javaagent] premain called, target=" + agentOps);
        instrument(agentOps, inst);
    }

    /**
     * Called when the agent is attached to a running JVM (e.g. via ByteBuddy).
     */
    public static void agentmain(String agentOps, Instrumentation inst) {
        System.out.println("[javaagent] agentmain called, target=" + agentOps);
        instrument(agentOps, inst);
    }

    /**
     * Registers the transformer and starts the metrics dashboard.
     *
     * @param agentOps the target class name passed as agent argument
     * @param inst     the JVM instrumentation handle
     */
    private static void instrument(String agentOps, Instrumentation inst) {
        System.out.println("[javaagent] adding transformer for target=" + agentOps);
        inst.addTransformer(new AOPTransformer(agentOps));

        // Launch dashboard on a daemon thread so it doesn't prevent JVM exit
        Thread dashboardThread = new Thread(MetricsDashboard::launch, "metrics-dashboard");
        dashboardThread.setDaemon(true);
        dashboardThread.start();
        System.out.println("[javaagent] metrics dashboard launched");
    }
}
