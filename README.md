# Java Agent APM — Lightweight Application Performance Monitor

A production-grade Java instrumentation agent that monitors application performance in real-time. Built on the Java Agent API and Javassist, it dynamically instruments methods at class-load time — **without modifying source code** — and provides a rich set of observability features comparable to tools like New Relic or Dynatrace, but in a lightweight, zero-dependency package.

---

## Features

### 🔍 Dynamic Bytecode Instrumentation
- Instruments Java class files at class-loading time using Javassist
- No source code changes or recompilation needed
- Configurable package/class/method filtering

### 📊 Real-time Performance Metrics
- **Call count**, **total time**, **average time**, **min/max time** per method
- Thread-safe `ConcurrentHashMap` + `AtomicLong` based registry
- Sub-microsecond overhead via `System.nanoTime()`

### 🐌 Slow Method Detection
- Configurable threshold (default: 100ms)
- Automatic detection and highlighting of slow methods
- Dedicated slow-methods view in the dashboard and HTTP API

### 🌳 Method Call Tree Tracing
- Tracks parent→child method relationships per thread
- `ThreadLocal` stack-based call tree construction
- Recent traces stored for dashboard/API inspection

### ❌ Error Tracking
- Counts exceptions per method
- Captures exception type (e.g. `java.lang.NullPointerException`)
- Calculates failure rate (errors / total calls)
- No swallowing — exceptions are re-thrown after recording

### 📡 Metrics Export
- **HTTP Server** (JDK built-in `HttpServer`, zero dependencies)
  - `GET /metrics` — full metrics as JSON
  - `GET /metrics/slow` — slow methods only
  - `GET /metrics/traces` — recent call trees
  - `GET /health` — health check + summary stats
  - `GET /config` — current agent configuration
  - `POST /config` — update config dynamically
- **JMX MBean** — viewable in JConsole / VisualVM
  - Attributes: `TotalMethodsTracked`, `TotalInvocations`, `TotalErrors`, `UptimeMs`
  - Operations: `setEnabled()`, `setSlowThreshold()`, `resetMetrics()`

### 💾 Persistence
- Periodic JSON dump to disk (every 30s, configurable)
- Automatic reload on agent restart
- Configurable export path

### 🖥️ Premium Desktop Dashboard
- **Light/Dark Theme Toggle** — Seamlessly switch between a modern dark mode and a clean light mode with persistent preference.
- **All Methods** tab — full table with search, color-coded rows (red=slow, orange=warning)
- **Slow Methods** tab — filtered view of threshold-exceeding methods
- **Call Traces** tab — JTree visualization of method call hierarchies (captures full tree if any branch exceeds the slow threshold limit!)
- **Configuration** tab — live controls for threshold, enable/disable, endpoint info
- Real-time stat cards (methods tracked, invocations, errors, uptime)
- **CSV Data Export** — Dump all table metrics to a clean CSV.

---

## Architecture

```text
com.thun.javaagent
├── agent/
│   └── AgentConfig.java          # Singleton runtime configuration
├── metrics/
│   ├── MethodMetrics.java        # Per-method stats (thread-safe)
│   └── MetricsRegistry.java      # Central metrics store + JSON serialization
├── tracing/
│   ├── CallNode.java             # Call tree node
│   └── CallTracer.java           # ThreadLocal call stack tracer
├── export/
│   ├── MetricsHttpServer.java    # Embedded HTTP JSON API
│   ├── MetricsJmxExporter.java   # JMX MBean
│   ├── MetricsJmxExporterMBean.java
│   └── MetricsPersistence.java   # Periodic JSON file dump
├── ui/
│   └── MetricsDashboard.java     # Swing tabbed dashboard (Dark/Light themes)
├── AgentMain.java                # Agent entry point (premain/agentmain)
├── AOPTransformer.java           # Javassist bytecode transformer
├── Test.java                     # Demo application
└── Sample.java                   # Alternative demo application
```

**Dependencies:** Javassist only. No Spring, no heavy UI frameworks.

---

## Prerequisites

- Java Development Kit (JDK) 8 or higher
- Apache Maven (included locally in `.tools/`)

---

## Building

The project includes a locally installed Maven binary in the `.tools` directory. Run this inside the project root:

**Windows:**
```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd clean package -DskipTests
```

**Linux/Mac:**
```bash
./.tools/apache-maven-3.9.9/bin/mvn clean package -DskipTests
```

This produces `javaagent-1.0-SNAPSHOT-jar-with-dependencies.jar` in the `target/` directory.

---

## Usage

### 🎨 1. Preview the Standalone UI Dashboard
If you just want to experience the Dashboard UI, Light/Dark toggle, and mock Call Traces without hooking it into an app:

**Windows:**
```powershell
java -cp "target\classes;target\dependency\*" com.thun.javaagent.ui.MetricsDashboard
```

**Linux / Mac:**
```bash
java -cp "target/classes:target/dependency/*" com.thun.javaagent.ui.MetricsDashboard
```

### 🚀 2. Run the Full Demo Application with Java Agent
Want to see the Java Agent actually profile an application in real-time? Run the built-in `Test.java` script with the agent attached:

**Windows:**
```powershell
java -javaagent:target/javaagent-1.0-SNAPSHOT-jar-with-dependencies.jar=com.thun -cp "target\classes;target\dependency\*" com.thun.javaagent.Test
```

**Linux / Mac:**
```bash
java -javaagent:target/javaagent-1.0-SNAPSHOT-jar-with-dependencies.jar=com.thun -cp "target/classes:target/dependency/*" com.thun.javaagent.Test
```

**This executes the following sequence:**
1. Injects the APM agent monitoring the `com.thun` namespace.
2. Starts executing the simulated queries, loops, and riskey methods inside `Test.java`.
3. Opens the **APM Dashboard** so you can watch metrics populate live.
4. Starts the localized **HTTP server** on `http://localhost:8686`.

### Agent Argument Format

You map the `Target Namespace` via passed parameters:

**Simple (Legacy):**
```bash
-javaagent:agent.jar=com.yourpackage
```

**Extended (Key=Value, semicolon-separated):**
```bash
-javaagent:agent.jar="target=com.yourpackage;threshold=150;port=9090;export=./my-metrics.json"
```

| Key | Default | Description |
|-----|---------|-------------|
| `target` | *(required)* | Package/class prefix to instrument |
| `threshold` | `100` | Slow method threshold in milliseconds |
| `port` | `8686` | HTTP server port |
| `export` | `./agent-metrics.json` | Metrics persistence file path |
| `persist` | `30` | Persistence interval in seconds |
| `enabled` | `true` | Enable/disable instrumentation |
| `packages` | *(empty)* | Comma-separated package prefixes to include |
| `exclude-classes` | *(empty)* | Comma-separated class names to exclude |
| `exclude-methods` | *(empty)* | Comma-separated method names to exclude |

### Attaching to Your Own Application

```bash
java -javaagent:path/to/javaagent-1.0-SNAPSHOT-jar-with-dependencies.jar="target=com.yourcompany.app;threshold=200" -jar your-application.jar
```

### HTTP API Examples

```bash
# Full metrics
curl http://localhost:8686/metrics

# Slow methods only
curl http://localhost:8686/metrics/slow

# Recent call traces
curl http://localhost:8686/metrics/traces

# Health check
curl http://localhost:8686/health

# Update config dynamically
curl -X POST http://localhost:8686/config -d '{"enabled": true, "slowThresholdMs": 200}'
```

---

## How It Compares

| Feature | This Agent | New Relic | Dynatrace |
|---------|-----------|-----------|-----------|
| Method-level timing | ✅ | ✅ | ✅ |
| Slow method detection | ✅ | ✅ | ✅ |
| Call tree tracing | ✅ | ✅ | ✅ |
| Error tracking | ✅ | ✅ | ✅ |
| HTTP API | ✅ | ✅ | ✅ |
| JMX integration | ✅ | ✅ | ✅ |
| Zero dependencies | ✅ | ❌ | ❌ |
| No cloud/SaaS required | ✅ | ❌ | ❌ |
| Open source | ✅ | ❌ | ❌ |
| Distributed tracing | ❌ | ✅ | ✅ |
| AI-powered insights | ❌ | ✅ | ✅ |

---

## License

This project is open source and available for educational and professional use.
