package com.lzy.javaagent;

/**
 * Demo application to exercise the Java Agent.
 * Run with:
 * <pre>
 * java -javaagent:target/javaagent-1.0-SNAPSHOT-jar-with-dependencies.jar=com.lzy.javaagent.Test \
 *      -cp target/javaagent-1.0-SNAPSHOT-jar-with-dependencies.jar \
 *      com.lzy.javaagent.Test
 * </pre>
 *
 * @author Ali
 */
public class Test {

    public void hello() {
        System.out.println("hello");
    }

    /**
     * Simulates a small computation so the dashboard shows varied timing.
     */
    public int compute(int n) {
        int sum = 0;
        for (int i = 0; i < n * 1000; i++) {
            sum += i;
        }
        return sum;
    }

    /**
     * Simulates a slow operation to demonstrate the slow-method highlighting.
     */
    public void slowOperation() {
        try {
            Thread.sleep(60); // > 50 ms → highlighted in dashboard
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Test t = new Test();

        System.out.println("=== Starting demo loop (20 iterations) ===");
        for (int i = 0; i < 20; i++) {
            t.hello();
            t.compute(i * 50);

            if (i % 5 == 0) {
                t.slowOperation();
            }

            Thread.sleep(300); // pause so the dashboard can show incremental updates
        }
        System.out.println("=== Demo complete — dashboard stays open ===");

        // Keep the JVM alive so the dashboard remains visible
        Thread.sleep(30_000);
    }
}
