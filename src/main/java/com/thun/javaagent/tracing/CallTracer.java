package com.thun.javaagent.tracing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Thread-aware call-tree tracer.
 * <p>
 * Uses a {@link ThreadLocal} stack to track the parent→child relationships
 * of method invocations within a single thread. When a root method completes,
 * the finished call tree is stored in a bounded queue for later inspection
 * by the dashboard or HTTP endpoint.
 * <p>
 * Called from bytecode injected by {@link com.thun.javaagent.AOPTransformer}.
 *
 * @author Ali
 */
public final class CallTracer {

    private static final CallTracer INSTANCE = new CallTracer();

    /** Maximum number of completed traces to keep in memory. */
    private static final int MAX_RECENT_TRACES = 100;

    /** Per-thread call stack. */
    private final ThreadLocal<Deque<CallNode>> callStack =
            ThreadLocal.withInitial(ArrayDeque::new);

    /** Recently completed root-level traces for the dashboard / HTTP API. */
    private final ConcurrentLinkedDeque<CallNode> recentTraces = new ConcurrentLinkedDeque<>();

    private CallTracer() { /* singleton */ }

    public static CallTracer getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------ API

    /**
     * Called at method entry. Pushes a new node onto the current thread's stack
     * and links it as a child of the caller (top of stack).
     *
     * @param methodKey identifier in the form "ClassName#methodName"
     */
    public void enterMethod(String methodKey) {
        Deque<CallNode> stack = callStack.get();
        CallNode node = new CallNode(methodKey, System.nanoTime(), Thread.currentThread().getName());

        if (!stack.isEmpty()) {
            stack.peek().addChild(node);
        }
        stack.push(node);
    }

    /**
     * Called at method exit. Pops the current node, sets its duration,
     * and—if it was the root—stores the completed trace.
     *
     * @param methodKey     identifier (used for sanity check)
     * @param durationNanos elapsed time in nanoseconds
     */
    public void exitMethod(String methodKey, long durationNanos) {
        Deque<CallNode> stack = callStack.get();
        if (stack.isEmpty()) return;

        CallNode node = stack.pop();
        node.setDurationNanos(durationNanos);

        // Store if root invocation OR if it took longer than slow threshold
        // This is crucial for long-running threads where stack never fully empties
        long thresholdNanos = com.thun.javaagent.agent.AgentConfig.getInstance().getSlowThresholdMs() * 1_000_000L;
        if (stack.isEmpty() || durationNanos >= thresholdNanos) {
            recentTraces.addFirst(node);
            while (recentTraces.size() > MAX_RECENT_TRACES) {
                recentTraces.pollLast();
            }
        }
    }

    // ------------------------------------------------------------ queries

    /**
     * Returns a snapshot of recently completed call trees (most recent first).
     */
    public List<CallNode> getRecentTraces() {
        return new ArrayList<>(recentTraces);
    }

    /**
     * Returns the most recent N traces.
     */
    public List<CallNode> getRecentTraces(int n) {
        List<CallNode> all = getRecentTraces();
        return all.subList(0, Math.min(n, all.size()));
    }

    /**
     * Clears all stored traces.
     */
    public void clear() {
        recentTraces.clear();
    }

    /**
     * Returns the current call depth for the calling thread.
     */
    public int getCurrentDepth() {
        return callStack.get().size();
    }
}
