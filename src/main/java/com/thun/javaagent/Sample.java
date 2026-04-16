package com.thun.javaagent;

import java.util.Random;

/**
 * A comprehensive test class designed to exercise the Java Agent metrics collection.
 * It simulates a variety of workloads (fast, slow, variable, CPU-intensive)
 * so that the Swing Dashboard displays a rich set of data.
 */
public class Sample {
    private final Random random = new Random();

    public void quickAction() {
        // Almost instantaneous
        Math.random(); 
    }

    public void databaseQuerySimulator() {
        try {
            // Simulates a slow database query (70ms - 120ms)
            Thread.sleep(70 + random.nextInt(50));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void networkCallSimulator() {
        try {
            // Highly variable network call (10ms - 200ms)
            Thread.sleep(10 + random.nextInt(190));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int dataCruncher(int heavyLevel) {
        int result = 0;
        // CPU intensive loop
        for (int i = 0; i < heavyLevel * 100_000; i++) {
            result += i ^ random.nextInt();
        }
        return result;
    }

    public void failingAction() {
        try {
            // Sometimes it fails!
            if (random.nextDouble() > 0.8) {
                throw new RuntimeException("Simulated Failure");
            }
        } catch (Exception e) {
            // caught
        }
    }

    public void runSimulation() {
        System.out.println("[Sample] Starting simulation loop...");
        for (int i = 0; i < 50; i++) {
            // Fire multiple quick actions
            for(int j=0; j<10; j++) quickAction();
            
            // Randomly call heavy workflows
            if (i % 3 == 0) databaseQuerySimulator();
            if (i % 2 == 0) networkCallSimulator();
            
            dataCruncher(5 + random.nextInt(10));
            failingAction();

            try {
                Thread.sleep(100); // 100ms pause between waves
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("[Sample] Simulation complete! Keeping JVM alive for dashboard...");
    }

    public static void main(String[] args) throws InterruptedException {
        // Keep everything running so dashboard stays open
        Sample sample = new Sample();
        sample.runSimulation();
        
        // Wait indefinitely to watch the dashboard
        Thread.sleep(600_000); // 10 mins
    }
}
