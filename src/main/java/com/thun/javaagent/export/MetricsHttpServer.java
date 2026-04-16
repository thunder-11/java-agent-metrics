package com.thun.javaagent.export;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.thun.javaagent.agent.AgentConfig;
import com.thun.javaagent.metrics.MetricsRegistry;
import com.thun.javaagent.tracing.CallNode;
import com.thun.javaagent.tracing.CallTracer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Embedded HTTP server for exposing agent metrics as JSON.
 * <p>
 * Uses {@code com.sun.net.httpserver.HttpServer} which is built into the JDK —
 * no external dependencies required.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code GET /metrics}       — full metrics as JSON</li>
 *   <li>{@code GET /metrics/slow}  — only slow methods</li>
 *   <li>{@code GET /metrics/traces}— recent call trees</li>
 *   <li>{@code GET /health}        — simple health check</li>
 *   <li>{@code GET /config}        — current agent configuration</li>
 *   <li>{@code POST /config}       — update configuration (enabled, threshold)</li>
 * </ul>
 *
 * @author Ali
 */
public final class MetricsHttpServer {

    private HttpServer server;
    private final int port;

    public MetricsHttpServer(int port) {
        this.port = port;
    }

    /**
     * Starts the HTTP server on a daemon thread. Non-blocking.
     */
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/metrics", this::handleMetrics);
            server.createContext("/metrics/slow", this::handleSlowMetrics);
            server.createContext("/metrics/traces", this::handleTraces);
            server.createContext("/health", this::handleHealth);
            server.createContext("/config", this::handleConfig);

            // Use a daemon thread executor
            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "agent-http");
                t.setDaemon(true);
                return t;
            }));

            server.start();
            System.out.println("[agent-http] server started on http://localhost:" + port);
        } catch (IOException e) {
            System.err.println("[agent-http] failed to start: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------ handlers

    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            return;
        }
        String json = MetricsRegistry.getInstance().toJson();
        sendResponse(exchange, 200, json);
    }

    private void handleSlowMetrics(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            return;
        }
        long threshold = AgentConfig.getInstance().getSlowThresholdMs();
        String json = MetricsRegistry.getInstance().slowMethodsToJson(threshold);
        sendResponse(exchange, 200, json);
    }

    private void handleTraces(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            return;
        }
        List<CallNode> traces = CallTracer.getInstance().getRecentTraces(20);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"count\": ").append(traces.size()).append(",\n  \"traces\": [\n");
        for (int i = 0; i < traces.size(); i++) {
            sb.append(traces.get(i).toJson());
            if (i < traces.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}");
        sendResponse(exchange, 200, sb.toString());
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        MetricsRegistry reg = MetricsRegistry.getInstance();
        AgentConfig cfg = AgentConfig.getInstance();
        String json = String.format(
                "{\n  \"status\": \"UP\",\n  \"enabled\": %b,\n  \"uptimeMs\": %d,\n  \"methods\": %d,\n  \"invocations\": %d,\n  \"errors\": %d\n}",
                cfg.isEnabled(), reg.getUptimeMillis(), reg.getMethodCount(),
                reg.getTotalInvocations(), reg.getTotalErrors()
        );
        sendResponse(exchange, 200, json);
    }

    private void handleConfig(HttpExchange exchange) throws IOException {
        AgentConfig cfg = AgentConfig.getInstance();

        if ("GET".equals(exchange.getRequestMethod())) {
            String json = String.format(
                    "{\n  \"enabled\": %b,\n  \"slowThresholdMs\": %d,\n  \"targetPackage\": \"%s\",\n  \"httpPort\": %d,\n  \"exportPath\": \"%s\"\n}",
                    cfg.isEnabled(), cfg.getSlowThresholdMs(), cfg.getTargetPackage(),
                    cfg.getHttpPort(), cfg.getMetricsExportPath().replace("\\", "\\\\")
            );
            sendResponse(exchange, 200, json);

        } else if ("POST".equals(exchange.getRequestMethod())) {
            // Read body
            String body;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                body = sb.toString();
            }

            // Simple parsing: look for "enabled" and "threshold" keys
            if (body.contains("\"enabled\"")) {
                boolean enabled = body.contains("\"enabled\": true") || body.contains("\"enabled\":true");
                cfg.setEnabled(enabled);
            }
            if (body.contains("\"slowThresholdMs\"")) {
                try {
                    int idx = body.indexOf("\"slowThresholdMs\"");
                    int colon = body.indexOf(":", idx);
                    int end = body.indexOf(",", colon);
                    if (end < 0) end = body.indexOf("}", colon);
                    long val = Long.parseLong(body.substring(colon + 1, end).trim());
                    cfg.setSlowThresholdMs(val);
                } catch (Exception ignored) { }
            }

            sendResponse(exchange, 200, "{\"status\": \"updated\", \"config\": " + cfg + "}");

        } else {
            sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
        }
    }

    // ------------------------------------------------------------ helpers

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
