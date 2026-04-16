package com.thun.javaagent;

import java.util.Random;

/**
 * Comprehensive demo application to exercise the Java Agent APM system.
 * <p>
 * Simulates realistic workloads including:
 * <ul>
 *   <li>Fast operations (sub-ms)</li>
 *   <li>Slow database/network simulations</li>
 *   <li>Nested method calls (to exercise call tree tracing)</li>
 *   <li>Methods that throw exceptions (to exercise error tracking)</li>
 *   <li>CPU-intensive computations</li>
 * </ul>
 *
 * Run with:
 * <pre>
 * java -javaagent:target/javaagent-1.0-SNAPSHOT-jar-with-dependencies.jar=com.thun.javaagent.Test -cp target/javaagent-1.0-SNAPSHOT-jar-with-dependencies.jar com.thun.javaagent.Test
 * </pre>
 *
 * @author Ali
 */
public class Test {

    private final Random random = new Random();

    // ─────────────────────────────────── Fast methods

    public void hello() {
        System.out.println("hello");
    }

    public int compute(int n) {
        int sum = 0;
        for (int i = 0; i < n * 1000; i++) {
            sum += i;
        }
        return sum;
    }

    // ─────────────────────────────────── Slow methods

    /**
     * Simulates a slow database query (80–150ms).
     */
    public String databaseQuery(String table) {
        try {
            Thread.sleep(80 + random.nextInt(70));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "rows from " + table;
    }

    /**
     * Simulates a network call with high latency variance (20–250ms).
     */
    public void networkCall() {
        try {
            Thread.sleep(20 + random.nextInt(230));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simulates a deliberately slow operation.
     */
    public void slowOperation() {
        try {
            Thread.sleep(120 + random.nextInt(80)); // 120–200ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ─────────────────────────────────── Nested method calls (for call tree)

    /**
     * Orchestrator that calls multiple sub-methods, creating a call tree.
     */
    public void processOrder() {
        validateOrder();
        String data = databaseQuery("orders");
        if (random.nextBoolean()) {
            networkCall();
        }
        finalizeOrder(data);
    }

    private void validateOrder() {
        compute(10 + random.nextInt(20));
    }

    private void finalizeOrder(String data) {
        try {
            Thread.sleep(15 + random.nextInt(25));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Another orchestrator to show different call paths.
     */
    public void generateReport() {
        String users = databaseQuery("users");
        String transactions = databaseQuery("transactions");
        compute(50 + random.nextInt(100));
    }

    // ─────────────────────────────────── Error-producing methods

    /**
     * Throws an exception ~30% of the time.
     */
    public void riskyPayment() {
        if (random.nextDouble() < 0.3) {
            throw new RuntimeException("Payment gateway timeout");
        }
    }

    /**
     * Throws different exception types randomly.
     */
    public void unreliableService() {
        double roll = random.nextDouble();
        if (roll < 0.15) {
            throw new IllegalStateException("Service unavailable");
        } else if (roll < 0.25) {
            throw new NullPointerException("Missing response body");
        }
    }

    // ─────────────────────────────────── Main

    public static void main(String[] args) throws InterruptedException {
        Test t = new Test();

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Java Agent APM — Demo Application         ║");
        System.out.println("║   Running 30 iterations with mixed workload ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();

        for (int i = 1; i <= 30; i++) {
            System.out.printf("[demo] ── iteration %d/30 ──%n", i);

            // Fast operations every iteration
            t.hello();
            t.compute(i * 10);

            // Nested call trees every 3rd iteration
            if (i % 3 == 0) {
                t.processOrder();
            }

            // Reports every 5th iteration
            if (i % 5 == 0) {
                t.generateReport();
            }

            // Slow operations every 4th iteration
            if (i % 4 == 0) {
                t.slowOperation();
            }

            // Network calls every 2nd iteration
            if (i % 2 == 0) {
                t.networkCall();
            }

            // Error-producing methods (wrapped in try/catch)
            try {
                t.riskyPayment();
            } catch (Exception e) {
                // Agent will still record the error
            }

            try {
                t.unreliableService();
            } catch (Exception e) {
                // Agent will still record the error
            }

            Thread.sleep(200);
        }

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Demo complete!                             ║");
        System.out.println("║   Dashboard & HTTP endpoints remain active   ║");
        System.out.println("║   Try: http://localhost:8686/metrics          ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        // Keep JVM alive for dashboard / HTTP interaction
        Thread.sleep(600_000); // 10 minutes
    }
}
