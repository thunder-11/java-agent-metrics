package com.thun.javaagent.metrics;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Singleton registry that stores runtime metrics for every instrumented method.
 * Thread-safe — designed for concurrent writes from instrumented bytecode and
 * concurrent reads from the Swing dashboard, HTTP server, and JMX.
 *
 * @author Ali
 */
public final class MetricsRegistry {

    private static final MetricsRegistry INSTANCE = new MetricsRegistry();

    private final ConcurrentHashMap<String, MethodMetrics> metricsMap = new ConcurrentHashMap<>();

    /** Timestamp when the registry was created (agent startup). */
    private final long startTimeMillis = System.currentTimeMillis();

    private MetricsRegistry() {
        // singleton
    }

    /**
     * @return the single global registry instance
     */
    public static MetricsRegistry getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------ recording

    /**
     * Records a method invocation. Called from injected bytecode.
     *
     * @param methodKey     identifier in the form "ClassName#methodName"
     * @param durationNanos elapsed nanoseconds for this invocation
     */
    public void updateMetric(String methodKey, long durationNanos) {
        metricsMap.computeIfAbsent(methodKey, k -> new MethodMetrics()).update(durationNanos);
    }

    /**
     * Records an exception thrown by a method. Called from injected bytecode.
     *
     * @param methodKey     identifier in the form "ClassName#methodName"
     * @param exceptionType fully-qualified exception class name
     */
    public void recordError(String methodKey, String exceptionType) {
        metricsMap.computeIfAbsent(methodKey, k -> new MethodMetrics()).recordError(exceptionType);
    }

    // ------------------------------------------------------------ queries

    /**
     * Returns a <b>snapshot copy</b> of the current metrics map.
     * Safe to iterate on the UI thread without risking ConcurrentModificationException.
     */
    public Map<String, MethodMetrics> getMetricsSnapshot() {
        return new LinkedHashMap<>(metricsMap);
    }

    /**
     * @return the live map (use only when snapshot semantics aren't needed)
     */
    public ConcurrentHashMap<String, MethodMetrics> getLiveMetrics() {
        return metricsMap;
    }

    /**
     * Returns methods whose average execution time exceeds the given threshold.
     */
    public Map<String, MethodMetrics> getSlowMethods(long thresholdMs) {
        Map<String, MethodMetrics> slow = new LinkedHashMap<>();
        for (Map.Entry<String, MethodMetrics> e : metricsMap.entrySet()) {
            if (e.getValue().getAverageTimeMs() > thresholdMs) {
                slow.put(e.getKey(), e.getValue());
            }
        }
        return slow;
    }

    /**
     * Returns the top N slowest methods sorted by average time descending.
     */
    public List<Map.Entry<String, MethodMetrics>> getTopSlowest(int n) {
        return metricsMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().getAverageTimeMs(), a.getValue().getAverageTimeMs()))
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * Returns methods that have recorded errors, sorted by error count descending.
     */
    public List<Map.Entry<String, MethodMetrics>> getErrorMethods() {
        return metricsMap.entrySet().stream()
                .filter(e -> e.getValue().getErrorCount() > 0)
                .sorted((a, b) -> Long.compare(b.getValue().getErrorCount(), a.getValue().getErrorCount()))
                .collect(Collectors.toList());
    }

    /**
     * @return total number of tracked methods
     */
    public int getMethodCount() {
        return metricsMap.size();
    }

    /**
     * @return total invocations across all methods
     */
    public long getTotalInvocations() {
        long total = 0;
        for (MethodMetrics m : metricsMap.values()) {
            total += m.getCallCount();
        }
        return total;
    }

    /**
     * @return total errors across all methods
     */
    public long getTotalErrors() {
        long total = 0;
        for (MethodMetrics m : metricsMap.values()) {
            total += m.getErrorCount();
        }
        return total;
    }

    /**
     * @return agent uptime in milliseconds
     */
    public long getUptimeMillis() {
        return System.currentTimeMillis() - startTimeMillis;
    }

    /**
     * Clears all collected metrics.
     */
    public void clear() {
        metricsMap.clear();
    }

    // ------------------------------------------------------------ JSON serialisation

    /**
     * Serialises the full metrics map to a JSON string (manual — no libraries).
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"uptimeMs\": ").append(getUptimeMillis()).append(",\n");
        sb.append("  \"totalMethods\": ").append(getMethodCount()).append(",\n");
        sb.append("  \"totalInvocations\": ").append(getTotalInvocations()).append(",\n");
        sb.append("  \"totalErrors\": ").append(getTotalErrors()).append(",\n");
        sb.append("  \"methods\": {\n");

        Iterator<Map.Entry<String, MethodMetrics>> it = metricsMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, MethodMetrics> entry = it.next();
            MethodMetrics m = entry.getValue();
            sb.append("    \"").append(escapeJson(entry.getKey())).append("\": {\n");
            sb.append("      \"callCount\": ").append(m.getCallCount()).append(",\n");
            sb.append("      \"totalTimeMs\": ").append(String.format("%.3f", m.getTotalTimeMs())).append(",\n");
            sb.append("      \"avgTimeMs\": ").append(String.format("%.3f", m.getAverageTimeMs())).append(",\n");
            sb.append("      \"minTimeMs\": ").append(String.format("%.3f", m.getMinTimeMs())).append(",\n");
            sb.append("      \"maxTimeMs\": ").append(String.format("%.3f", m.getMaxTimeMs())).append(",\n");
            sb.append("      \"errorCount\": ").append(m.getErrorCount()).append(",\n");
            sb.append("      \"errorRate\": ").append(String.format("%.4f", m.getErrorRate())).append(",\n");
            sb.append("      \"lastException\": \"").append(escapeJson(m.getLastExceptionType())).append("\"\n");
            sb.append("    }");
            if (it.hasNext()) sb.append(",");
            sb.append("\n");
        }

        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Serialises only slow methods to JSON.
     */
    public String slowMethodsToJson(long thresholdMs) {
        Map<String, MethodMetrics> slow = getSlowMethods(thresholdMs);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"thresholdMs\": ").append(thresholdMs).append(",\n");
        sb.append("  \"count\": ").append(slow.size()).append(",\n");
        sb.append("  \"methods\": {\n");

        Iterator<Map.Entry<String, MethodMetrics>> it = slow.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, MethodMetrics> entry = it.next();
            MethodMetrics m = entry.getValue();
            sb.append("    \"").append(escapeJson(entry.getKey())).append("\": {\n");
            sb.append("      \"avgTimeMs\": ").append(String.format("%.3f", m.getAverageTimeMs())).append(",\n");
            sb.append("      \"maxTimeMs\": ").append(String.format("%.3f", m.getMaxTimeMs())).append(",\n");
            sb.append("      \"callCount\": ").append(m.getCallCount()).append("\n");
            sb.append("    }");
            if (it.hasNext()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }\n}");
        return sb.toString();
    }

    // ------------------------------------------------------------ persistence

    /**
     * Dumps the current metrics to a JSON file.
     */
    public void dumpToFile(String path) {
        try (Writer w = new BufferedWriter(new FileWriter(path))) {
            w.write(toJson());
            w.flush();
        } catch (IOException e) {
            System.err.println("[agent-persist] failed to write metrics: " + e.getMessage());
        }
    }

    /**
     * Loads metrics from a previously dumped JSON file.
     * Uses simple line-based parsing — no JSON library needed.
     */
    public void loadFromFile(String path) {
        File f = new File(path);
        if (!f.exists() || !f.isFile()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line.trim());
            }
            parseJson(sb.toString());
            System.out.println("[agent-persist] loaded metrics from " + path);
        } catch (Exception e) {
            System.err.println("[agent-persist] failed to load metrics: " + e.getMessage());
        }
    }

    /**
     * Minimal JSON parser that restores method metrics from the format produced by {@link #toJson()}.
     */
    private void parseJson(String json) {
        // Find the "methods" block
        int methodsIdx = json.indexOf("\"methods\"");
        if (methodsIdx < 0) return;

        // Find each method entry: "com.foo.Bar#baz": { ... }
        int searchFrom = methodsIdx;
        while (true) {
            int keyStart = json.indexOf("\"", searchFrom + 1);
            if (keyStart < 0) break;
            int keyEnd = json.indexOf("\"", keyStart + 1);
            if (keyEnd < 0) break;

            String key = json.substring(keyStart + 1, keyEnd);

            // Skip non-method keys
            if (!key.contains("#")) {
                searchFrom = keyEnd + 1;
                continue;
            }

            // Find the { ... } block for this method
            int blockStart = json.indexOf("{", keyEnd);
            if (blockStart < 0) break;
            int blockEnd = json.indexOf("}", blockStart);
            if (blockEnd < 0) break;

            String block = json.substring(blockStart, blockEnd + 1);

            long calls = extractLong(block, "callCount");
            long totalNanos = (long) (extractDouble(block, "totalTimeMs") * 1_000_000);
            long minNanos = (long) (extractDouble(block, "minTimeMs") * 1_000_000);
            long maxNanos = (long) (extractDouble(block, "maxTimeMs") * 1_000_000);
            long errors = extractLong(block, "errorCount");
            String lastEx = extractString(block, "lastException");

            MethodMetrics m = new MethodMetrics();
            m.restore(calls, totalNanos, minNanos, maxNanos, errors, lastEx);
            metricsMap.put(key, m);

            searchFrom = blockEnd + 1;
        }
    }

    // ------------------------------------------------------------ helpers

    private static long extractLong(String block, String key) {
        int idx = block.indexOf("\"" + key + "\"");
        if (idx < 0) return 0;
        int colon = block.indexOf(":", idx);
        if (colon < 0) return 0;
        int end = block.indexOf(",", colon);
        if (end < 0) end = block.indexOf("}", colon);
        if (end < 0) return 0;
        try {
            return Long.parseLong(block.substring(colon + 1, end).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double extractDouble(String block, String key) {
        int idx = block.indexOf("\"" + key + "\"");
        if (idx < 0) return 0;
        int colon = block.indexOf(":", idx);
        if (colon < 0) return 0;
        int end = block.indexOf(",", colon);
        if (end < 0) end = block.indexOf("}", colon);
        if (end < 0) return 0;
        try {
            return Double.parseDouble(block.substring(colon + 1, end).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String extractString(String block, String key) {
        int idx = block.indexOf("\"" + key + "\"");
        if (idx < 0) return "";
        int colon = block.indexOf(":", idx);
        if (colon < 0) return "";
        int qStart = block.indexOf("\"", colon + 1);
        if (qStart < 0) return "";
        int qEnd = block.indexOf("\"", qStart + 1);
        if (qEnd < 0) return "";
        return block.substring(qStart + 1, qEnd);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
