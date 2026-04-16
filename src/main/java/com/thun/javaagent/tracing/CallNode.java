package com.thun.javaagent.tracing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single node in a method call tree.
 * <p>
 * Each node captures the method key, start time, duration, and any child
 * method invocations that occurred while this method was executing.
 * Nodes are created by {@link CallTracer} during bytecode-injected
 * enter/exit method hooks.
 *
 * @author Ali
 */
public class CallNode {

    private final String methodKey;
    private final long startNanos;
    private final String threadName;
    private long durationNanos;
    private final List<CallNode> children = new ArrayList<>();

    public CallNode(String methodKey, long startNanos, String threadName) {
        this.methodKey = methodKey;
        this.startNanos = startNanos;
        this.threadName = threadName;
    }

    // ------------------------------------------------------------ mutators

    public void setDurationNanos(long durationNanos) {
        this.durationNanos = durationNanos;
    }

    public void addChild(CallNode child) {
        children.add(child);
    }

    // ------------------------------------------------------------ accessors

    public String getMethodKey()     { return methodKey; }
    public long getStartNanos()      { return startNanos; }
    public long getDurationNanos()   { return durationNanos; }
    public String getThreadName()    { return threadName; }

    public double getDurationMs() {
        return durationNanos / 1_000_000.0;
    }

    public List<CallNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public int getTotalNodeCount() {
        int count = 1;
        for (CallNode child : children) {
            count += child.getTotalNodeCount();
        }
        return count;
    }

    // ------------------------------------------------------------ serialisation

    /**
     * Renders this node (and children recursively) as a JSON string.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        toJson(sb, 0);
        return sb.toString();
    }

    private void toJson(StringBuilder sb, int depth) {
        String indent = repeatStr("  ", depth);
        sb.append(indent).append("{\n");
        sb.append(indent).append("  \"method\": \"").append(escapeJson(methodKey)).append("\",\n");
        sb.append(indent).append("  \"thread\": \"").append(escapeJson(threadName)).append("\",\n");
        sb.append(indent).append("  \"durationMs\": ").append(String.format("%.3f", getDurationMs())).append(",\n");
        sb.append(indent).append("  \"children\": [");
        if (children.isEmpty()) {
            sb.append("]\n");
        } else {
            sb.append("\n");
            for (int i = 0; i < children.size(); i++) {
                children.get(i).toJson(sb, depth + 2);
                if (i < children.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append(indent).append("  ]\n");
        }
        sb.append(indent).append("}");
    }

    @Override
    public String toString() {
        return String.format("%s (%.2fms, %d children)", methodKey, getDurationMs(), children.size());
    }

    // ------------------------------------------------------------ helpers

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String repeatStr(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}
